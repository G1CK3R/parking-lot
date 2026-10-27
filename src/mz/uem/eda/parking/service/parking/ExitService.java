package mz.uem.eda.parking.service.parking;

import java.time.LocalDateTime;
import java.util.Objects;

import mz.uem.eda.parking.model.Lane;
import mz.uem.eda.parking.model.Move;
import mz.uem.eda.parking.model.ParkingSession;
import mz.uem.eda.parking.model.RetrievalPlan;
import mz.uem.eda.parking.model.Vehicle;
import mz.uem.eda.parking.service.billing.BillingService;
import mz.uem.eda.parking.structures.Stack;

/**
 * Tira um carro do parque. Encontra-o por matrícula ou por talão, com uma
 * busca sequencial pelas baias, e executa a saída nas pilhas reais: os k
 * carros de cima passam para a baia de manobra, o carro sai, e os k voltam
 * pela ordem inversa. A baia fica como estava, sem o carro que saiu.
 *
 * <p>Enquanto executa, regista cada passo num {@link RetrievalPlan}, que
 * devolve à interface para ela reproduzir (ADR-014). Quando a interface o
 * recebe, as pilhas já estão no estado final.
 *
 * <p>Tirar um carro à profundidade k custa 2k manobras: é o custo que o
 * relatório analisa e a porta de 1 de Novembro verifica.
 */
public class ExitService {

    private final Iterable<Lane> lanes;
    private final Lane buffer;

    /**
     * Cria o serviço sobre as baias do parque. As baias são as mesmas do
     * {@link EntryService}, incluindo a baia de manobra, que o serviço
     * encontra entre elas.
     *
     * @param lanes as baias do parque, com exactamente uma baia de manobra
     * @throws NullPointerException se {@code lanes} for {@code null}
     * @throws IllegalArgumentException se não houver baia de manobra, ou se
     *         houver mais de uma
     * @complexity O(B), em que B é o número de baias
     */
    public ExitService(Iterable<Lane> lanes) {
        this.lanes = Objects.requireNonNull(lanes, "lanes não pode ser null");
        Lane found = null;
        for (Lane lane : lanes) {
            if (lane.isBuffer()) {
                if (found != null) {
                    throw new IllegalArgumentException("há mais de uma baia de manobra: "
                            + found.getCode() + " e " + lane.getCode());
                }
                found = lane;
            }
        }
        if (found == null) {
            throw new IllegalArgumentException("falta a baia de manobra");
        }
        this.buffer = found;
    }

    /**
     * Encontra a sessão do carro com esta matrícula, sem o tirar da baia.
     * Serve para a interface mostrar o carro e cobrar antes da saída (RF08):
     * primeiro {@code findByPlate}, depois o {@link BillingService}, e só no
     * fim {@link #exitByTicket} com o talão da sessão encontrada.
     *
     * @param plate a matrícula escrita pelo operador
     * @return a sessão activa, ou {@code null} se o carro não estiver no
     *         parque
     * @complexity O(n), em que n é o número de carros estacionados
     */
    public ParkingSession findByPlate(String plate) {
        String wanted = Vehicle.normalizePlate(plate);
        for (Lane lane : lanes) {
            if (!lane.isBuffer()) {
                int depth = depthOfPlate(lane, wanted);
                if (depth >= 0) {
                    return sessionAt(lane, depth);
                }
            }
        }
        return null;
    }

    /**
     * Encontra a sessão com este número de talão, sem tirar o carro da baia.
     *
     * @param ticketNumber o número do talão
     * @return a sessão activa, ou {@code null} se nenhum carro no parque
     *         tiver este talão
     * @complexity O(n), em que n é o número de carros estacionados
     */
    public ParkingSession findByTicket(int ticketNumber) {
        for (Lane lane : lanes) {
            if (!lane.isBuffer()) {
                int depth = depthOfTicket(lane, ticketNumber);
                if (depth >= 0) {
                    return sessionAt(lane, depth);
                }
            }
        }
        return null;
    }

    /**
     * Tira do parque o carro com esta matrícula. A matrícula é normalizada
     * antes de comparar, por isso «aaa-123-mc» encontra «AAA 123 MC».
     *
     * @param plate a matrícula escrita pelo operador
     * @param exitTime a hora de saída
     * @return o plano com os movimentos executados, ou {@code null} se o
     *         carro não estiver no parque
     * @throws IllegalArgumentException se a hora de saída for anterior à
     *         entrada (o parque não muda)
     * @throws IllegalStateException se a baia de manobra não tiver espaço
     *         para os carros de cima (o parque não muda)
     * @complexity O(n + k): procura nos n carros estacionados e faz 2k + 1
     *             movimentos
     */
    public RetrievalPlan exitByPlate(String plate, LocalDateTime exitTime) {
        Objects.requireNonNull(exitTime, "exitTime não pode ser null");
        String wanted = Vehicle.normalizePlate(plate);
        for (Lane lane : lanes) {
            if (!lane.isBuffer()) {
                int depth = depthOfPlate(lane, wanted);
                if (depth >= 0) {
                    return retrieve(lane, depth, exitTime);
                }
            }
        }
        return null;
    }

    /**
     * Tira do parque o carro com este número de talão.
     *
     * @param ticketNumber o número do talão
     * @param exitTime a hora de saída
     * @return o plano com os movimentos executados, ou {@code null} se
     *         nenhum carro no parque tiver este talão
     * @throws IllegalArgumentException se a hora de saída for anterior à
     *         entrada (o parque não muda)
     * @throws IllegalStateException se a baia de manobra não tiver espaço
     *         para os carros de cima (o parque não muda)
     * @complexity O(n + k), como {@link #exitByPlate}
     */
    public RetrievalPlan exitByTicket(int ticketNumber, LocalDateTime exitTime) {
        Objects.requireNonNull(exitTime, "exitTime não pode ser null");
        for (Lane lane : lanes) {
            if (!lane.isBuffer()) {
                int depth = depthOfTicket(lane, ticketNumber);
                if (depth >= 0) {
                    return retrieve(lane, depth, exitTime);
                }
            }
        }
        return null;
    }

    /**
     * Executa a saída do carro que está à profundidade {@code depth} da baia
     * e regista cada passo no plano. Valida tudo antes de mexer em qualquer
     * carro: se lançar uma excepção, o parque fica como estava.
     *
     * @complexity O(k): k leituras para chegar ao carro e 2k + 1 movimentos
     */
    private RetrievalPlan retrieve(Lane lane, int depth, LocalDateTime exitTime) {
        ParkingSession target = sessionAt(lane, depth);
        if (exitTime.isBefore(target.getEntryTime())) {
            throw new IllegalArgumentException("exitTime anterior à entrada: " + exitTime);
        }
        if (buffer.freeSpace() < depth) {
            throw new IllegalStateException("a baia de manobra não tem espaço para "
                    + depth + " carros");
        }

        Stack<ParkingSession> stack = lane.asStack();
        Stack<ParkingSession> hold = buffer.asStack();
        RetrievalPlan plan = new RetrievalPlan(target, depth);
        int sequence = 1;

        // 1. Os k carros de cima passam para a baia de manobra.
        for (int i = 0; i < depth; i++) {
            ParkingSession moved = stack.pop();
            hold.push(moved);
            plan.addMove(new Move(sequence, plateOf(moved), lane.getCode(), buffer.getCode()));
            sequence++;
        }

        // 2. O carro fica no topo e sai do parque.
        stack.pop();
        plan.addMove(new Move(sequence, plateOf(target), lane.getCode(), null));
        sequence++;

        // 3. Os k voltam, pela ordem inversa: a baia fica na ordem original.
        for (int i = 0; i < depth; i++) {
            ParkingSession moved = hold.pop();
            stack.push(moved);
            plan.addMove(new Move(sequence, plateOf(moved), buffer.getCode(), lane.getCode()));
            sequence++;
        }

        target.close(exitTime, plan.getManoeuvreCount());
        return plan;
    }

    /**
     * Profundidade do carro com esta matrícula: quantos carros tem por cima.
     * Percorre a baia só para leitura, do topo para o fundo.
     *
     * @return a profundidade, ou -1 se a matrícula não estiver na baia
     * @complexity O(c), em que c é o número de carros na baia
     */
    private static int depthOfPlate(Lane lane, String plate) {
        int depth = 0;
        for (ParkingSession session : lane.contents()) {
            if (session.getVehicle().getPlate().equals(plate)) {
                return depth;
            }
            depth++;
        }
        return -1;
    }

    /**
     * Profundidade do carro com este talão.
     *
     * @return a profundidade, ou -1 se o talão não estiver na baia
     * @complexity O(c), em que c é o número de carros na baia
     */
    private static int depthOfTicket(Lane lane, int ticketNumber) {
        int depth = 0;
        for (ParkingSession session : lane.contents()) {
            if (session.getTicketNumber() == ticketNumber) {
                return depth;
            }
            depth++;
        }
        return -1;
    }

    /**
     * A sessão que está à profundidade indicada, sem a tirar da baia.
     *
     * @complexity O(depth)
     */
    private static ParkingSession sessionAt(Lane lane, int depth) {
        int i = 0;
        for (ParkingSession session : lane.contents()) {
            if (i == depth) {
                return session;
            }
            i++;
        }
        throw new IllegalStateException("profundidade fora da baia: " + depth);
    }

    private static String plateOf(ParkingSession session) {
        return session.getVehicle().getPlate();
    }
}