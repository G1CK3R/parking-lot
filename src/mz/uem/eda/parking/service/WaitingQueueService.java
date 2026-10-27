package mz.uem.eda.parking.service;

import java.time.LocalDateTime;
import java.util.Objects;

import mz.uem.eda.parking.model.Lane;
import mz.uem.eda.parking.model.ParkingSession;
import mz.uem.eda.parking.model.Vehicle;
import mz.uem.eda.parking.service.policy.ServicePolicy;
import mz.uem.eda.parking.structures.LinkedQueue;
import mz.uem.eda.parking.structures.Queue;

/**
 * As filas de espera à entrada, para quando o parque está cheio. Há duas
 * filas FIFO separadas, a preferencial e a normal, e uma
 * {@link ServicePolicy} que decide de qual sai o próximo carro. É assim que
 * se tem prioridade sem fila de prioridade (ADR-007): nenhuma fila é
 * ordenada, cada uma é atendida pela ordem de chegada.
 *
 * <p>Um carro vai para a fila preferencial se o operador o marcar à entrada
 * como cliente preferencial (idoso, grávida, pessoa com deficiência); todos
 * os outros vão para a normal.
 *
 * <p>Fluxo previsto: o {@link EntryService} devolve {@code null} com o parque
 * cheio e a interface chama {@link #join}; depois de cada saída, chama
 * {@link #admitNext} para o primeiro da fila escolhida entrar no lugar
 * libertado.
 *
 * <p>Se houver reservas, os lugares reservados não contam como livres: um
 * carro da fila de espera só entra se sobrar lugar depois das reservas
 * activas (RF11).
 *
 * <p>As duas filas são {@link LinkedQueue}, porque não há limite conhecido
 * para quantos carros podem esperar.
 */
public class WaitingQueueService {

    private final Iterable<Lane> lanes;
    private final EntryService entryService;
    private final ServicePolicy policy;
    private final ReservationService reservations;
    private final Queue<WaitingCar> preferential;
    private final Queue<WaitingCar> normal;

    /**
     * Cria o serviço com as duas filas vazias, num parque sem reservas.
     *
     * @param lanes as baias do parque, as mesmas do {@link EntryService}
     * @param entryService o serviço que faz entrar o carro atendido
     * @param policy a política de atendimento
     * @throws NullPointerException se algum argumento for {@code null}
     */
    public WaitingQueueService(Iterable<Lane> lanes, EntryService entryService,
            ServicePolicy policy) {
        this(lanes, entryService, policy, null);
    }

    /**
     * Cria o serviço com as duas filas vazias, respeitando as reservas.
     *
     * @param lanes as baias do parque, as mesmas do {@link EntryService}
     * @param entryService o serviço que faz entrar o carro atendido
     * @param policy a política de atendimento
     * @param reservations as reservas do parque, ou {@code null} se não
     *        houver
     * @throws NullPointerException se {@code lanes}, {@code entryService} ou
     *         {@code policy} for {@code null}
     */
    public WaitingQueueService(Iterable<Lane> lanes, EntryService entryService,
            ServicePolicy policy, ReservationService reservations) {
        this.lanes = Objects.requireNonNull(lanes, "lanes não pode ser null");
        this.entryService = Objects.requireNonNull(entryService, "entryService não pode ser null");
        this.policy = Objects.requireNonNull(policy, "policy não pode ser null");
        this.reservations = reservations;
        this.preferential = new LinkedQueue<>();
        this.normal = new LinkedQueue<>();
    }

    /**
     * Põe o carro no fim da fila normal.
     *
     * @param vehicle a viatura que chegou
     * @param estimatedMinutes a duração estimada, em minutos
     * @param arrivedAt a hora de chegada
     * @return o registo do carro em espera
     * @throws IllegalArgumentException se a duração não for positiva
     * @throws IllegalStateException se o carro já estiver estacionado ou já
     *         estiver à espera
     * @complexity O(n + w): procura a matrícula nos n carros estacionados e
     *             nos w em espera; enfileirar é O(1)
     */
    public WaitingCar join(Vehicle vehicle, int estimatedMinutes, LocalDateTime arrivedAt) {
        return join(vehicle, estimatedMinutes, arrivedAt, false);
    }

    /**
     * Põe o carro no fim da fila indicada pelo balcão: a preferencial, se o
     * operador marcou o cliente como preferencial à entrada (idoso, grávida,
     * pessoa com deficiência), ou a normal.
     *
     * @param vehicle a viatura que chegou
     * @param estimatedMinutes a duração estimada, em minutos
     * @param arrivedAt a hora de chegada
     * @param preferentialCustomer se vai para a fila preferencial
     * @return o registo do carro em espera
     * @throws IllegalStateException se o carro já estiver estacionado ou já
     *         estiver à espera
     * @complexity O(n + w)
     */
    public WaitingCar join(Vehicle vehicle, int estimatedMinutes, LocalDateTime arrivedAt,
            boolean preferentialCustomer) {
        Objects.requireNonNull(vehicle, "vehicle não pode ser null");
        if (entryService.isParked(vehicle)) {
            throw new IllegalStateException("veículo já parqueado: " + vehicle.getPlate());
        }
        if (isWaiting(vehicle)) {
            throw new IllegalStateException("veículo já está à espera: " + vehicle.getPlate());
        }
        WaitingCar car = new WaitingCar(vehicle, estimatedMinutes, arrivedAt, preferentialCustomer);
        if (car.isPreferential()) {
            preferential.enqueue(car);
        } else {
            normal.enqueue(car);
        }
        return car;
    }

    /**
     * Faz entrar o próximo carro em espera, se houver lugar. A política só é
     * consultada quando há carros à espera e alguma baia tem espaço, porque
     * uma política pode guardar estado (a rotação conta quantos atendeu de
     * cada fila).
     *
     * <p>O carro só sai da fila depois de o {@link EntryService} o ter
     * estacionado. Se a política de colocação não lhe encontrar baia, o carro
     * fica à frente da fila e o método devolve {@code null}.
     *
     * @param now a hora de entrada do carro atendido
     * @return a sessão do carro que entrou, ou {@code null} se não houver
     *         ninguém à espera ou se não houver lugar sem reserva
     * @complexity O(B + n): verifica as B baias e o {@link EntryService}
     *             procura a matrícula nos n carros estacionados
     */
    public ParkingSession admitNext(LocalDateTime now) {
        Objects.requireNonNull(now, "now não pode ser null");
        if (isEmpty() || !hasFreeSpace(now)) {
            return null;
        }
        boolean fromPreferential = policy.servePreferential(preferential.size(), normal.size());
        Queue<WaitingCar> queue = fromPreferential ? preferential : normal;

        WaitingCar car = queue.peek();
        ParkingSession session = entryService.enter(car.getVehicle(), car.getEstimatedMinutes(), now);
        if (session == null) {
            return null;
        }
        queue.dequeue();
        return session;
    }

    /**
     * Diz se a viatura está numa das filas. Percorre as duas filas só para
     * leitura.
     *
     * @param vehicle a viatura a procurar
     * @return {@code true} se a matrícula estiver à espera
     * @complexity O(w), em que w é o número de carros em espera
     */
    public boolean isWaiting(Vehicle vehicle) {
        Objects.requireNonNull(vehicle, "vehicle não pode ser null");
        return contains(preferential, vehicle) || contains(normal, vehicle);
    }

    /**
     * Tira o carro da fila onde está, sem que entre no parque: o operador
     * desfez a ida para a fila. Tirar do meio de uma fila faz-se só com
     * operações de fila: uma volta completa, voltando a enfileirar todos
     * menos esse. A ordem dos outros fica igual.
     *
     * @param vehicle a viatura
     * @return {@code true} se estava à espera e saiu
     * @complexity O(w)
     */
    public boolean remove(Vehicle vehicle) {
        Objects.requireNonNull(vehicle, "vehicle não pode ser null");
        return removeFrom(preferential, vehicle) || removeFrom(normal, vehicle);
    }

    private static boolean removeFrom(Queue<WaitingCar> queue, Vehicle vehicle) {
        boolean removed = false;
        int size = queue.size();
        for (int i = 0; i < size; i++) {
            WaitingCar car = queue.dequeue();
            if (!removed && car.getVehicle().equals(vehicle)) {
                removed = true;
            } else {
                queue.enqueue(car);
            }
        }
        return removed;
    }

    /** @return quantos carros esperam na fila preferencial; O(1) */
    public int preferentialSize() {
        return preferential.size();
    }

    /** @return quantos carros esperam na fila normal; O(1) */
    public int normalSize() {
        return normal.size();
    }

    /** @return {@code true} se ninguém estiver à espera; O(1) */
    public boolean isEmpty() {
        return preferential.isEmpty() && normal.isEmpty();
    }

    /**
     * Percurso só de leitura da fila preferencial, da frente para o fim.
     * Não alterar o serviço durante o percurso (ver secção 1.1 da
     * Justificação de métodos).
     *
     * @return os carros em espera na fila preferencial
     * @complexity O(1) para obter; O(w) para percorrer
     */
    public Iterable<WaitingCar> preferentialContents() {
        return preferential;
    }

    /**
     * Percurso só de leitura da fila normal, da frente para o fim.
     *
     * @return os carros em espera na fila normal
     * @complexity O(1) para obter; O(w) para percorrer
     */
    public Iterable<WaitingCar> normalContents() {
        return normal;
    }

    /**
     * @return {@code true} se houver um lugar livre que não esteja
     *         reservado
     * @complexity O(B)
     */
    private boolean hasFreeSpace(LocalDateTime now) {
        if (reservations != null) {
            return reservations.availableForWalkIns(now) > 0;
        }
        for (Lane lane : lanes) {
            if (!lane.isBuffer() && !lane.isFull()) {
                return true;
            }
        }
        return false;
    }

    private static boolean contains(Queue<WaitingCar> queue, Vehicle vehicle) {
        for (WaitingCar car : queue) {
            if (car.getVehicle().equals(vehicle)) {
                return true;
            }
        }
        return false;
    }
}