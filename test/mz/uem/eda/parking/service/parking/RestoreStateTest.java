package mz.uem.eda.parking.service.parking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import mz.uem.eda.parking.model.Lane;
import mz.uem.eda.parking.model.Reservation;
import mz.uem.eda.parking.model.StayClass;
import mz.uem.eda.parking.model.Vehicle;
import mz.uem.eda.parking.service.policy.FirstWithSpace;
import mz.uem.eda.parking.structures.LinkedQueue;
import mz.uem.eda.parking.structures.Queue;

/** O que os serviços oferecem à {@code persistence} para repor o estado. */
class RestoreStateTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 10, 21, 8, 0);

    @Test
    void entryContinuesTheTicketNumbering() {
        EntryService entry = new EntryService(lanes(), new FirstWithSpace());
        entry.restoreNextTicketNumber(42);

        assertEquals(42, entry.enter(new Vehicle("AAA001MC", Vehicle.Type.CAR), 60, T0).getTicketNumber());
        assertEquals(43, entry.getNextTicketNumber());
        assertThrows(IllegalArgumentException.class, () -> entry.restoreNextTicketNumber(0));
    }

    @Test
    void reservationsAreLoadedInCreationOrder() {
        Queue<Lane> lanes = lanes();
        ReservationService service = new ReservationService(lanes, new EntryService(lanes, new FirstWithSpace()), 30);
        Reservation first = reservation("AAA001MC", T0);
        Reservation second = reservation("AAA002MC", T0.plusMinutes(5));

        service.load(first);
        service.load(second);

        assertEquals(2, service.activeCount());
        assertEquals(1, service.expire(T0.plusMinutes(30)));                // a primeira expira primeiro
        assertThrows(IllegalArgumentException.class, () -> service.load(reservation("AAA003MC", T0)));
        assertThrows(IllegalStateException.class, () -> service.load(reservation("AAA002MC", T0.plusMinutes(6))));
    }

    @Test
    void onlyActiveReservationsAreLoaded() {
        Queue<Lane> lanes = lanes();
        ReservationService service = new ReservationService(lanes, new EntryService(lanes, new FirstWithSpace()), 30);
        Reservation expired = reservation("AAA001MC", T0);
        expired.expire();

        assertThrows(IllegalArgumentException.class, () -> service.load(expired));
    }

    private static Reservation reservation(String plate, LocalDateTime createdAt) {
        return new Reservation(plate, StayClass.SHORT, 60, createdAt, createdAt.plusMinutes(30));
    }

    private static Queue<Lane> lanes() {
        Queue<Lane> lanes = new LinkedQueue<>();
        lanes.enqueue(Lane.parking("L01", 5, StayClass.FULL_DAY));
        lanes.enqueue(Lane.buffer("M", 10));
        return lanes;
    }
}
