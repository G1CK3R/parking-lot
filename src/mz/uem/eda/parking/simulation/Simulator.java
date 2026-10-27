package mz.uem.eda.parking.simulation;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Objects;

import mz.uem.eda.parking.model.Customer;
import mz.uem.eda.parking.model.Lane;
import mz.uem.eda.parking.model.ParkingSession;
import mz.uem.eda.parking.model.RetrievalPlan;
import mz.uem.eda.parking.model.StayClass;
import mz.uem.eda.parking.model.Vehicle;
import mz.uem.eda.parking.service.EntryService;
import mz.uem.eda.parking.service.ExitService;
import mz.uem.eda.parking.service.WaitingQueueService;
import mz.uem.eda.parking.service.policy.PlacementPolicy;
import mz.uem.eda.parking.service.policy.RoundRobin;
import mz.uem.eda.parking.service.policy.ServicePolicy;
import mz.uem.eda.parking.simulation.ArrivalGenerator.Arrival;
import mz.uem.eda.parking.structures.LinkedQueue;
import mz.uem.eda.parking.structures.Queue;

/**
 * Simula um dia de parque, minuto a minuto, com os serviços reais
 * ({@link EntryService}, {@link ExitService}, {@link WaitingQueueService}) e
 * as políticas que se querem comparar. A mesma estrutura, algoritmos
 * diferentes: é aqui que se vê a diferença entre as políticas.
 *
 * <p><b>Em cada minuto, por esta ordem:</b>
 * <ol>
 *   <li>saem os carros cuja hora de saída chegou, e cada saída conta as
 *       manobras do plano (2k);</li>
 *   <li>entram os carros da fila de espera, enquanto houver lugar, pela
 *       política de atendimento;</li>
 *   <li>chegam os carros deste minuto: entram se houver lugar e ninguém à
 *       espera; senão, vão para o fim da fila;</li>
 *   <li>regista-se a ocupação, quantos lugares ficaram ocupados (RF19).</li>
 * </ol>
 *
 * <p><b>Só pilhas e filas, também aqui.</b> As chegadas são uma fila
 * ordenada por minuto (o radix sort do {@link ArrivalGenerator}): a «fila de
 * eventos» consome-se pela frente. Os carros estacionados ficam numa fila
 * que se percorre uma vez por minuto, desenfileirando cada carro e voltando
 * a enfileirar os que ficam. Com 200 lugares e 1080 minutos, são cerca de
 * 200 000 operações O(1) por dia simulado: menos de um segundo.
 *
 * <p>O dia simulado vai das 6h00 à meia-noite. Os carros de permanência
 * longa ficam para lá do fim.
 *
 * <p>O {@link #main} corre a comparação completa ({@link Comparison}) e
 * grava o CSV em {@code docs/measurements/simulation.csv}. O ecrã de
 * simulação da interface corre a mesma comparação.
 */
public class Simulator {

    /** Primeiro e último minuto simulados, desde a meia-noite. */
    public static final int DAY_START = 6 * 60;
    public static final int DAY_END = 24 * 60;

    /** O dia de calendário usado para as horas das sessões (é indiferente qual). */
    private static final LocalDateTime MIDNIGHT = LocalDateTime.of(2026, 11, 2, 0, 0);

    /** O cliente dos carros preferenciais: só interessa o {@code isPreferential()}. */
    private static final Customer PREFERENTIAL = new Customer("Cliente preferencial", null, true);

    /**
     * Simula um dia.
     *
     * @param layout o nome da configuração, para o CSV
     * @param lanes as baias, vazias, incluindo a de manobra
     * @param placement a política de colocação
     * @param service a política de atendimento (nova: pode guardar estado)
     * @param arrivals as chegadas do dia, ordenadas por minuto; a fila é
     *        consumida
     * @param seed a semente que gerou as chegadas, para o CSV
     * @return as métricas do dia
     * @complexity O(M · n), M = minutos simulados, n = carros no parque
     */
    public Metrics run(String layout, Queue<Lane> lanes, PlacementPolicy placement, ServicePolicy service,
            Queue<Arrival> arrivals, long seed) {
        Objects.requireNonNull(lanes, "lanes não pode ser null");
        Objects.requireNonNull(arrivals, "arrivals não pode ser null");
        Metrics metrics = new Metrics(layout, nameOf(placement), nameOf(service), seed, capacityOf(lanes));
        Stopwatch stopwatch = new Stopwatch();
        stopwatch.start();

        EntryService entry = new EntryService(lanes, placement);
        ExitService exit = new ExitService(lanes);
        WaitingQueueService waiting = new WaitingQueueService(lanes, entry, service);
        Queue<ParkedCar> parked = new LinkedQueue<>();
        Queue<Arrival> waitingArrivals = new LinkedQueue<>();

        for (int minute = DAY_START; minute < DAY_END; minute++) {
            LocalDateTime now = MIDNIGHT.plusMinutes(minute);

            // 1. Saídas: uma volta à fila dos estacionados.
            int count = parked.size();
            for (int i = 0; i < count; i++) {
                ParkedCar car = parked.dequeue();
                if (car.departureMinute <= minute) {
                    RetrievalPlan plan = exit.exitByTicket(car.ticketNumber, now);
                    metrics.recordExit(plan.getManoeuvreCount(), minute);
                } else {
                    parked.enqueue(car);
                }
            }

            // 2. A fila de espera ocupa os lugares libertados.
            ParkingSession admitted = waiting.admitNext(now);
            while (admitted != null) {
                Arrival arrival = removeByPlate(waitingArrivals, admitted.getVehicle().getPlate());
                park(parked, metrics, arrival, admitted, minute);
                admitted = waiting.admitNext(now);
            }

            // 3. Chegadas deste minuto.
            while (!arrivals.isEmpty() && arrivals.peek().getArrivalMinute() <= minute) {
                Arrival arrival = arrivals.dequeue();
                metrics.recordArrival();
                Vehicle vehicle = new Vehicle(arrival.getPlate(), Vehicle.Type.CAR,
                        arrival.isPreferential() ? PREFERENTIAL : null);
                ParkingSession session = null;
                if (waiting.isEmpty()) {
                    session = entry.enter(vehicle, arrival.getDeclaredMinutes(), now);
                }
                if (session != null) {
                    park(parked, metrics, arrival, session, minute);
                } else {
                    waiting.join(vehicle, arrival.getDeclaredMinutes(), now);
                    waitingArrivals.enqueue(arrival);
                }
            }

            // 4. Ocupação no fim do minuto.
            metrics.recordOccupancy(minute, parked.size());
        }

        metrics.setStillWaiting(waiting.preferentialSize() + waiting.normalSize());
        stopwatch.stop();
        metrics.setRunMillis(stopwatch.elapsedMillis());
        return metrics;
    }

    /** Quantos lugares têm as baias, sem a de manobra. */
    private static int capacityOf(Iterable<Lane> lanes) {
        int places = 0;
        for (Lane lane : lanes) {
            if (!lane.isBuffer()) {
                places += lane.getCapacity();
            }
        }
        return places;
    }

    /** Regista a entrada e marca a hora a que o carro vai sair. */
    private static void park(Queue<ParkedCar> parked, Metrics metrics, Arrival arrival,
            ParkingSession session, int minute) {
        metrics.recordEntry(arrival.isPreferential(), minute - arrival.getArrivalMinute());
        parked.enqueue(new ParkedCar(session.getTicketNumber(), minute + arrival.getStayMinutes()));
    }

    /**
     * Tira da fila a chegada com esta matrícula, dando uma volta à fila. A
     * ordem dos outros fica igual.
     *
     * @complexity O(w), w = carros à espera
     */
    private static Arrival removeByPlate(Queue<Arrival> queue, String plate) {
        Arrival found = null;
        int count = queue.size();
        for (int i = 0; i < count; i++) {
            Arrival arrival = queue.dequeue();
            if (found == null && Vehicle.normalizePlate(arrival.getPlate()).equals(plate)) {
                found = arrival;
            } else {
                queue.enqueue(arrival);
            }
        }
        if (found == null) {
            throw new IllegalStateException("carro atendido que não estava à espera: " + plate);
        }
        return found;
    }

    // ---- configurações das baias -----------------------------------------

    /**
     * A configuração real (ADR-006): 4 baias curtas de 5, 16 de jornada de
     * 10, 2 longas de 10, mais a manobra de 10. 200 lugares.
     */
    public static Queue<Lane> heterogeneousLanes() {
        Queue<Lane> lanes = new LinkedQueue<>();
        addLanes(lanes, "C", 4, 5, StayClass.SHORT);
        addLanes(lanes, "J", 16, 10, StayClass.FULL_DAY);
        addLanes(lanes, "P", 2, 10, StayClass.LONG_TERM);
        lanes.enqueue(Lane.buffer("M", 10));
        return lanes;
    }

    /**
     * A configuração de comparação: 20 baias iguais, de 10, com as mesmas
     * classes por proporção (4 curtas, 14 de jornada, 2 longas), mais a
     * manobra de 10. Também 200 lugares: muda só a forma.
     */
    public static Queue<Lane> homogeneousLanes() {
        Queue<Lane> lanes = new LinkedQueue<>();
        addLanes(lanes, "C", 4, 10, StayClass.SHORT);
        addLanes(lanes, "J", 14, 10, StayClass.FULL_DAY);
        addLanes(lanes, "P", 2, 10, StayClass.LONG_TERM);
        lanes.enqueue(Lane.buffer("M", 10));
        return lanes;
    }

    private static void addLanes(Queue<Lane> lanes, String prefix, int count, int depth, StayClass stayClass) {
        for (int i = 1; i <= count; i++) {
            lanes.enqueue(Lane.parking(prefix + (i < 10 ? "0" : "") + i, depth, stayClass));
        }
    }

    // ---- comparação completa -----------------------------------------------

    /** Quantos dias (sementes) se simulam por combinação. */
    public static final int DAYS = 20;

    /** Carros por dia: mais do que os 200 lugares, para haver fila de manhã. */
    public static final int CARS_PER_DAY = 260;

    /** Percentagem de carros de clientes preferenciais. */
    public static final int PREFERENTIAL_PERCENT = 15;

    /** Erro máximo da duração declarada, em percentagem da real. */
    public static final int ESTIMATE_ERROR_PERCENT = 20;

    /**
     * Corre a comparação completa ({@link Comparison}: 2 configurações × 4
     * políticas de colocação × 2 de atendimento, {@value #DAYS} dias cada,
     * sempre com os mesmos dias para todas). Grava uma linha por dia em
     * {@code docs/measurements/simulation.csv} e escreve na consola a média
     * de cada combinação.
     *
     * @param args não usados
     * @throws IOException se não conseguir gravar o CSV
     */
    public static void main(String[] args) throws IOException {
        Comparison comparison = new Comparison(
                new ArrivalGenerator(CARS_PER_DAY, PREFERENTIAL_PERCENT, ESTIMATE_ERROR_PERCENT), DAYS);
        Path out = Paths.get("docs", "measurements", "simulation.csv");
        Files.createDirectories(out.getParent());

        try (PrintWriter csv = new PrintWriter(Files.newBufferedWriter(out, StandardCharsets.UTF_8))) {
            csv.println(Metrics.csvHeader());
            System.out.println("configuração   colocação        atendimento      manobras/saída  pico(min)  "
                    + "espera P méd/máx  espera N méd/máx  sem lugar  ocupação  hora mais cheia");
            for (int i = 0; i < comparison.size(); i++) {
                Summary summary = comparison.run(i);
                for (Metrics day : summary.days()) {
                    csv.println(day.toCsvRow());
                }
                System.out.println(String.format(Locale.ROOT,
                        "%-14s %-16s %-16s %14.2f %10.0f %9.0f/%-6d %9.0f/%-6d %9d %8.0f%% %10dh",
                        summary.getLayout(), summary.getPlacement(), summary.getService(),
                        summary.getAverageManoeuvres(), summary.getPeakManoeuvreMinutes(),
                        summary.getPreferentialAverageWait(), summary.getPreferentialMaxWait(),
                        summary.getNormalAverageWait(), summary.getNormalMaxWait(), summary.getNotServed(),
                        summary.getAverageOccupancyPercent(), summary.getBusiestHour()));
            }
        }
        System.out.println();
        System.out.println("CSV gravado em " + out.toAbsolutePath());
    }

    /** O nome curto da política, para o CSV; a rotação leva o k. */
    static String nameOf(Object policy) {
        if (policy instanceof RoundRobin) {
            return "RoundRobin(" + ((RoundRobin) policy).getPreferentialTurns() + ")";
        }
        return policy.getClass().getSimpleName();
    }

    /** Um carro estacionado, do ponto de vista do simulador: o talão e quando sai. */
    private static final class ParkedCar {
        private final int ticketNumber;
        private final int departureMinute;

        ParkedCar(int ticketNumber, int departureMinute) {
            this.ticketNumber = ticketNumber;
            this.departureMinute = departureMinute;
        }
    }
}
