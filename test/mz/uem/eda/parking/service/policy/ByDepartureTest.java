package mz.uem.eda.parking.service.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import mz.uem.eda.parking.model.Lane;
import mz.uem.eda.parking.model.ParkingSession;
import mz.uem.eda.parking.model.StayClass;
import mz.uem.eda.parking.model.Vehicle;
import mz.uem.eda.parking.service.parking.EntryService;
import mz.uem.eda.parking.structures.LinkedQueue;
import mz.uem.eda.parking.structures.Queue;

/** A ByDeparture: empilhar só sobre quem sai depois, com horas em vez de classes. */
class ByDepartureTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 11, 2, 8, 0);
    private static final ByDeparture POLICY = new ByDeparture();
    private int ticket = 1;

    @Test
    void choosesTheTightestLaneWhoseTopLeavesLater() {
        Lane a = Lane.parking("A", 10, StayClass.FULL_DAY);
        Lane b = Lane.parking("B", 10, StayClass.FULL_DAY);
        Lane c = Lane.parking("C", 10, StayClass.FULL_DAY);
        park(a, 10 * 60);          // sai às 18h00
        park(b, 8 * 60);           // sai às 16h00
        park(c, 9 * 60);           // sai às 17h00

        // Sai às 15h30: as três servem; fica a do topo que sai mais cedo (B, 16h).
        assertSame(b, POLICY.chooseLane(lanes(a, b, c), StayClass.FULL_DAY, T0.plusMinutes(7 * 60 + 30)));
        // Sai às 16h30: B já não serve; fica C (17h), não A (18h).
        assertSame(c, POLICY.chooseLane(lanes(a, b, c), StayClass.FULL_DAY, T0.plusMinutes(8 * 60 + 30)));
    }

    @Test
    void leavingAtTheSameTimeAsTheTopDoesNotBlockIt() {
        Lane a = Lane.parking("A", 10, StayClass.FULL_DAY);
        Lane empty = Lane.parking("B", 10, StayClass.FULL_DAY);
        park(a, 60);

        assertSame(a, POLICY.chooseLane(lanes(a, empty), StayClass.SHORT, T0.plusMinutes(60)));
    }

    @Test
    void anEmptyLaneComesBeforeBlockingSomeone() {
        Lane a = Lane.parking("A", 10, StayClass.FULL_DAY);
        Lane empty = Lane.parking("B", 10, StayClass.FULL_DAY);
        park(a, 60);                // sai às 9h00

        assertSame(empty, POLICY.chooseLane(lanes(a, empty), StayClass.FULL_DAY, T0.plusHours(9)));
    }

    @Test
    void whenEveryoneWouldBeBlockedItBlocksTheOneThatLeavesLast() {
        Lane a = Lane.parking("A", 10, StayClass.FULL_DAY);
        Lane b = Lane.parking("B", 10, StayClass.FULL_DAY);
        park(a, 60);                // sai às 9h00
        park(b, 3 * 60);            // sai às 11h00

        assertSame(b, POLICY.chooseLane(lanes(a, b), StayClass.FULL_DAY, T0.plusHours(9)));
    }

    @Test
    void skipsFullLanesAndTheBufferAndReturnsNullWhenThereIsNoSpace() {
        Lane buffer = Lane.buffer("M", 10);
        Lane full = Lane.parking("A", 1, StayClass.FULL_DAY);
        park(full, 10 * 60);

        assertNull(POLICY.chooseLane(lanes(buffer, full), StayClass.FULL_DAY, T0.plusHours(1)));
    }

    @Test
    void theClassOfTheLaneDoesNotMatter() {
        Lane longTerm = Lane.parking("P", 10, StayClass.LONG_TERM);
        Lane fullDay = Lane.parking("J", 10, StayClass.FULL_DAY);
        park(longTerm, 3 * 24 * 60);
        park(fullDay, 30);          // sai às 8h30

        // Um carro de curta duração que sai às 9h00 vai para cima do de longa duração.
        assertSame(longTerm, POLICY.chooseLane(lanes(longTerm, fullDay), StayClass.SHORT, T0.plusHours(1)));
    }

    @Test
    void theEntryServicePassesTheDeclaredExitTime() {
        Lane a = Lane.parking("A", 10, StayClass.FULL_DAY);
        Lane b = Lane.parking("B", 10, StayClass.FULL_DAY);
        EntryService entry = new EntryService(lanes(a, b), POLICY);
        entry.enter(new Vehicle("AAA001MC", Vehicle.Type.CAR), 9 * 60, T0);      // A, sai às 17h
        entry.enter(new Vehicle("AAA002MC", Vehicle.Type.CAR), 10 * 60, T0);     // B: não bloqueia A
        ParkingSession third = entry.enter(new Vehicle("AAA003MC", Vehicle.Type.CAR), 8 * 60, T0);

        // Sai às 16h: o encaixe mais justo é A (17h), não B (18h).
        assertEquals("A", third.getLaneCode());
        assertEquals("B", entry.enter(new Vehicle("AAA004MC", Vehicle.Type.CAR), 9 * 60 + 30, T0)
                .getLaneCode());
    }

    // ------------------------------------------------------------------

    /** Estaciona um carro que entra às 8h00 e diz que fica {@code minutes}. */
    private void park(Lane lane, int minutes) {
        Vehicle vehicle = new Vehicle("CAR" + ticket, Vehicle.Type.CAR);
        lane.asStack().push(new ParkingSession(ticket, vehicle, lane.getCode(), EntryService.classify(minutes),
                minutes, T0));
        ticket++;
    }

    private static Queue<Lane> lanes(Lane... all) {
        Queue<Lane> queue = new LinkedQueue<>();
        for (Lane lane : all) {
            queue.enqueue(lane);
        }
        return queue;
    }
}
