package mz.uem.eda.parking.service.views;

import mz.uem.eda.parking.model.ParkingSession;
import mz.uem.eda.parking.model.Reservation;
import mz.uem.eda.parking.model.Vehicle;

/**
 * Uma operação do operador que se pode desfazer. A {@code ParkingDesk}
 * empilha-as numa pilha de operações: desfazer tira a do topo, a última, e
 * faz o contrário. Desfazer várias vezes desfaz pela ordem inversa, como
 * qualquer pilha.
 *
 * <p>Só se desfazem entradas e reservas. As saídas não, porque já houve
 * dinheiro: por isso a pilha esvazia-se a cada saída, e também quando o
 * operador muda ou quando um carro entra da fila de espera (a ordem das
 * baias deixou de ser a das operações empilhadas).
 */
public final class Operation {

    /** O tipo de operação. */
    public enum Type {
        /** Um carro entrou numa baia. Desfazer tira-o do topo e anula o talão. */
        ENTRY,
        /** Um carro ficou na fila de espera. Desfazer tira-o da fila. */
        QUEUED,
        /** Fez-se uma reserva. Desfazer cancela-a. */
        RESERVATION,
        /** Cancelou-se uma reserva. Desfazer volta a activá-la. */
        CANCELLATION
    }

    private final Type type;
    private final ParkingSession session;
    private final Vehicle vehicle;
    private final Reservation reservation;

    private Operation(Type type, ParkingSession session, Vehicle vehicle, Reservation reservation) {
        this.type = type;
        this.session = session;
        this.vehicle = vehicle;
        this.reservation = reservation;
    }

    public static Operation entry(ParkingSession session) {
        return new Operation(Type.ENTRY, session, session.getVehicle(), null);
    }

    public static Operation queued(Vehicle vehicle) {
        return new Operation(Type.QUEUED, null, vehicle, null);
    }

    public static Operation reservation(Reservation reservation) {
        return new Operation(Type.RESERVATION, null, null, reservation);
    }

    public static Operation cancellation(Reservation reservation) {
        return new Operation(Type.CANCELLATION, null, null, reservation);
    }

    public Type getType() {
        return type;
    }

    /** @return a matrícula a que a operação diz respeito */
    public String getPlate() {
        return reservation != null ? reservation.getPlate() : vehicle.getPlate();
    }

    /** @return a sessão de uma entrada, ou {@code null} nos outros tipos */
    public ParkingSession getSession() {
        return session;
    }

    public Vehicle getVehicle() {
        return vehicle;
    }

    public Reservation getReservation() {
        return reservation;
    }
}
