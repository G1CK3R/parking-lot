package mz.uem.eda.parking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import mz.uem.eda.parking.model.Lane;
import mz.uem.eda.parking.model.ParkingSession;
import mz.uem.eda.parking.model.Reservation;
import mz.uem.eda.parking.model.StayClass;
import mz.uem.eda.parking.model.Vehicle;
import mz.uem.eda.parking.service.policy.FirstWithSpace;
import mz.uem.eda.parking.service.policy.StrictPriority;
import mz.uem.eda.parking.structures.LinkedQueue;
import mz.uem.eda.parking.structures.Queue;

class ReservationServiceTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 10, 20, 8, 0);
    private static final int TOLERANCE = 30;

    private Queue<Lane> lanes;
    private EntryService entry;
    private ReservationService reservations;

    @BeforeEach
    void setUp() {
        lanes = new LinkedQueue<>();
        lanes.enqueue(Lane.parking("L01", 3, StayClass.FULL_DAY));
        lanes.enqueue(Lane.buffer("M", 10));
        entry = new EntryService(lanes, new FirstWithSpace());
        reservations = new ReservationService(lanes, entry, TOLERANCE);
    }

    // ---- reservar -------------------------------------------------------

    @Test
    void reserveHoldsAPlaceForTheTolerance() {
        Reservation r = reservations.reserve("aaa-001-mc", 90, T0);

        assertEquals("AAA001MC", r.getPlate());
        assertEquals(StayClass.SHORT, r.getStayClass());
        assertEquals(T0.plusMinutes(TOLERANCE), r.getExpiresAt());
        assertTrue(r.isActive());
        assertEquals(1, reservations.activeCount());
        assertEquals(2, reservations.availableForWalkIns(T0));
    }

    @Test
    void cannotReserveMorePlacesThanAreFree() {
        entry.enter(car("PPP001MC"), 60, T0);
        assertTrue(reservations.reserve("AAA001MC", 60, T0) != null);
        assertTrue(reservations.reserve("AAA002MC", 60, T0) != null);

        assertNull(reservations.reserve("AAA003MC", 60, T0));
        assertEquals(0, reservations.availableForWalkIns(T0));
    }

    @Test
    void rejectsRepeatedOrParkedPlates() {
        reservations.reserve("AAA001MC", 60, T0);
        entry.enter(car("PPP001MC"), 60, T0);

        assertThrows(IllegalStateException.class, () -> reservations.reserve("aaa 001 mc", 60, T0));
        assertThrows(IllegalStateException.class, () -> reservations.reserve("PPP001MC", 60, T0));
        assertThrows(IllegalArgumentException.class, () -> reservations.reserve("BBB001MC", 0, T0));
    }

    // ---- check-in -------------------------------------------------------

    @Test
    void checkInOpensASessionAndFreesTheReservation() {
        Reservation r = reservations.reserve("AAA001MC", 90, T0);
        ParkingSession session = reservations.checkIn(car("AAA001MC"), T0.plusMinutes(10));

        assertEquals("AAA001MC", session.getVehicle().getPlate());
        assertEquals(90, session.getEstimatedMinutes());
        assertEquals(T0.plusMinutes(10), session.getEntryTime());
        assertEquals(Reservation.Status.CHECKED_IN, r.getStatus());
        assertEquals(0, reservations.activeCount());
        assertTrue(entry.isParked(car("AAA001MC")));
        assertEquals(2, reservations.availableForWalkIns(T0.plusMinutes(10)));
    }

    @Test
    void checkInWithoutReservationReturnsNull() {
        assertNull(reservations.checkIn(car("AAA001MC"), T0));
    }

    @Test
    void checkInAfterTheToleranceFails() {
        Reservation r = reservations.reserve("AAA001MC", 60, T0);

        assertNull(reservations.checkIn(car("AAA001MC"), T0.plusMinutes(TOLERANCE)));
        assertEquals(Reservation.Status.EXPIRED, r.getStatus());
    }

    @Test
    void checkInWithoutPlaceKeepsTheReservation() {
        Reservation r = reservations.reserve("AAA001MC", 60, T0);
        // três entradas sem reserva, sem consultar availableForWalkIns
        entry.enter(car("PPP001MC"), 60, T0);
        entry.enter(car("PPP002MC"), 60, T0);
        entry.enter(car("PPP003MC"), 60, T0);

        assertThrows(IllegalStateException.class, () -> reservations.checkIn(car("AAA001MC"), T0));
        assertTrue(r.isActive());
    }

    // ---- expiração em FIFO ----------------------------------------------

    @Test
    void expiresInCreationOrder() {
        Reservation a = reservations.reserve("AAA001MC", 60, T0);
        Reservation b = reservations.reserve("AAA002MC", 60, T0.plusMinutes(10));
        Reservation c = reservations.reserve("AAA003MC", 60, T0.plusMinutes(20));

        assertEquals(0, reservations.expire(T0.plusMinutes(29)));
        assertEquals(2, reservations.expire(T0.plusMinutes(40)));

        assertEquals(Reservation.Status.EXPIRED, a.getStatus());
        assertEquals(Reservation.Status.EXPIRED, b.getStatus());
        assertTrue(c.isActive());
        assertEquals(1, reservations.activeCount());
    }

    @Test
    void checkedInReservationsLeaveWhenTheyReachTheFront() {
        reservations.reserve("AAA001MC", 60, T0);
        reservations.reserve("AAA002MC", 60, T0.plusMinutes(5));
        reservations.checkIn(car("AAA002MC"), T0.plusMinutes(6));

        assertEquals(1, reservations.activeCount());
        assertEquals(1, reservations.expire(T0.plusMinutes(31)));   // só AAA001MC expira
        assertEquals(0, reservations.activeCount());
        assertEquals(0, count(reservations.reservations()));        // a fila ficou vazia
    }

    // ---- fila de espera respeita as reservas ----------------------------

    @Test
    void waitingQueueDoesNotTakeReservedPlaces() {
        WaitingQueueService waiting = new WaitingQueueService(lanes, entry, new StrictPriority(), reservations);
        entry.enter(car("PPP001MC"), 60, T0);
        entry.enter(car("PPP002MC"), 60, T0);
        reservations.reserve("AAA001MC", 60, T0);                   // o último lugar
        waiting.join(car("WWW001MC"), 60, T0);

        assertNull(waiting.admitNext(T0.plusMinutes(5)));
        assertEquals(1, waiting.normalSize());

        // a reserva expira e o lugar passa para a fila de espera
        assertEquals("WWW001MC", waiting.admitNext(T0.plusMinutes(TOLERANCE)).getVehicle().getPlate());
    }

    // ---- cancelar ---------------------------------------------------------

    @Test
    void cancelFreesThePlaceAndLeavesWhenItReachesTheFront() {
        Reservation first = reservations.reserve("AAA001MC", 60, T0);
        Reservation second = reservations.reserve("AAA002MC", 60, T0.plusMinutes(5));

        assertEquals(second, reservations.cancel("aaa-002-mc", T0.plusMinutes(10)));
        assertEquals(Reservation.Status.CANCELLED, second.getStatus());
        assertEquals(1, reservations.activeCount());
        assertEquals(2, reservations.availableForWalkIns(T0.plusMinutes(10)));
        assertNull(reservations.cancel("AAA002MC", T0.plusMinutes(11)));
        assertNull(reservations.checkIn(car("AAA002MC"), T0.plusMinutes(12)));

        // A primeira expira; a cancelada, que estava atrás, sai logo a seguir.
        reservations.expire(T0.plusMinutes(TOLERANCE));
        assertEquals(Reservation.Status.EXPIRED, first.getStatus());
        assertEquals(0, count(reservations.reservations()));
        assertEquals(0, reservations.activeCount());
    }

    @Test
    void anExpiredReservationCannotBeCancelled() {
        Reservation r = reservations.reserve("AAA001MC", 60, T0);

        assertNull(reservations.cancel("AAA001MC", T0.plusMinutes(TOLERANCE)));
        assertEquals(Reservation.Status.EXPIRED, r.getStatus());
        assertThrows(IllegalStateException.class, r::cancel);
    }

    // ---- Reservation ----------------------------------------------------

    @Test
    void reservationStateOnlyMovesOnce() {
        Reservation r = new Reservation("AAA001MC", StayClass.SHORT, 60, T0, T0.plusMinutes(30));
        r.checkIn();
        assertThrows(IllegalStateException.class, r::expire);
        assertThrows(IllegalStateException.class, r::checkIn);
        assertThrows(IllegalArgumentException.class,
                () -> new Reservation("AAA001MC", StayClass.SHORT, 60, T0, T0));
        assertThrows(IllegalArgumentException.class, () -> new ReservationService(lanes, entry, 0));
    }

    // ---- auxiliares -----------------------------------------------------

    private static Vehicle car(String plate) {
        return new Vehicle(plate, Vehicle.Type.CAR, null);
    }

    private static int count(Iterable<Reservation> all) {
        int n = 0;
        for (Reservation r : all) {
            n++;
        }
        return n;
    }
}
