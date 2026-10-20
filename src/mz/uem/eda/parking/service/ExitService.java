package mz.uem.eda.parking.service;

import java.time.LocalDateTime;
import java.util.Objects;

import mz.uem.eda.parking.model.Lane;
import mz.uem.eda.parking.model.Move;
import mz.uem.eda.parking.model.ParkingSession;
import mz.uem.eda.parking.model.RetrievalPlan;
import mz.uem.eda.parking.model.Vehicle;

import mz.uem.eda.parking.structures.Stack;

public class ExitService {

    private final Lane buffer;
    private final Iterable<Lane> lanes;

    public ExitService(Iterable<Lane> lanes) {
        this.lanes = Objects.requireNonNull(lanes, "lanes não pode ser null");
        Lane found = null;
        for(Lane lane: lanes){
            if (lane.isBuffer()) {
                if(found != null) {
                   throw new IllegalArgumentException("há mais de uma baia de manobra: "
                            + found.getCode() + " e " + lane.getCode());    
                }
                found = lane;
            }
            
        }

        if (found == null) {
            throw new IllegalArgumentException("Falta a baia de manobra");
        }
        this.buffer = found;
    }

    /** Devolve o plano executado, ou null se o carro não estiver no parque. */
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
    /** Quantos carros estão por cima; -1 se não estiver na baia. Só leitura. */
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

    public RetrievalPlan exitByTicket(int ticketNumber, LocalDateTime exitTime) {
        Objects.requireNonNull(exitTime, "exitTime não pode ser null");
        for(Lane lane: lanes) {
            if (!lane.isBuffer()) {
                int depth = depthOfTicket(lane, ticketNumber);
                if (depth >= 0) {
                    return retrieve(lane, depth, exitTime);
                }
            }
        }
        return null;
    }
    
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
     * Executa a saída do carro que está à profundidade {@code depth} da baia
     * e regista cada passo no plano. Valida tudo antes de mexer em qualquer
     * carro: se lançar uma excepção, o parque fica como estava.
     *
     * @complexity O(k): k leituras para chegar ao carro e 2k + 1 movimentos
     */
    private RetrievalPlan retrieve(Lane lane, int depth, LocalDateTime exitTime){
        ParkingSession target =  sessionAt(lane, depth);
        if(exitTime.isBefore(target.getEntryTime())){
            throw new IllegalArgumentException("exitTime anterior à entrada: "+exitTime);
        }
         if (buffer.freeSpace() < depth) {
            throw new IllegalStateException("a baia de manobra não tem espaço para "
                    + depth + " carros");
        }

        Stack<ParkingSession> stack = lane.asStack();
        Stack<ParkingSession> hold  = buffer.asStack();
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
