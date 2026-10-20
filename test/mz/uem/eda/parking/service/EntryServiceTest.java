package mz.uem.eda.parking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import mz.uem.eda.parking.model.Lane;
import mz.uem.eda.parking.model.ParkingSession;
import mz.uem.eda.parking.model.StayClass;
import mz.uem.eda.parking.model.Vehicle;
import mz.uem.eda.parking.service.policy.FirstWithSpace;
import mz.uem.eda.parking.structures.LinkedQueue;
import mz.uem.eda.parking.structures.Queue;

class EntryServiceTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 10, 18, 8, 0);

    private Lane lane;
    private EntryService entry;

    @BeforeEach
    void setUp() {
        lane = Lane.parking("L01", 2, StayClass.FULL_DAY);
        Queue<Lane> lanes = new LinkedQueue<>();
        lanes.enqueue(lane);
        lanes.enqueue(Lane.buffer("M", 10));
        entry = new EntryService(lanes, new FirstWithSpace());
    }

    // ---- classificação (ADR-004) ----------------------------------------

    @Test
    void shortStayUpToTwoHours() {
        assertEquals(StayClass.SHORT, EntryService.classify(1));
        assertEquals(StayClass.SHORT, EntryService.classify(120));
        assertEquals(StayClass.FULL_DAY, EntryService.classify(121));
    }

    @Test
    void fullDayUpToTwentyFourHours() {
        assertEquals(StayClass.FULL_DAY, EntryService.classify(1440));
        assertEquals(StayClass.LONG_TERM, EntryService.classify(1441));
    }

    @Test
    void rejectsNonPositiveDuration() {
        assertThrows(IllegalArgumentException.class, () -> EntryService.classify(0));
        assertThrows(IllegalArgumentException.class, () -> EntryService.classify(-5));
    }

    // ---- entrada --------------------------------------------------------

    @Test
    void sessionGoesOnTopOfTheLane() {
        ParkingSession session = entry.enter(car("AAA001MC"), 90, T0);

        assertSame(session, lane.asStack().peek());
        assertEquals("L01", session.getLaneCode());
        assertEquals(StayClass.SHORT, session.getStayClass());
        assertEquals(90, session.getEstimatedMinutes());
        assertEquals(T0, session.getEntryTime());
        assertTrue(session.isActive());
    }

    @Test
    void ticketsAreConsecutive() {
        assertEquals(1, entry.getNextTicketNumber());
        assertEquals(1, entry.enter(car("AAA001MC"), 60, T0).getTicketNumber());
        assertEquals(2, entry.enter(car("AAA002MC"), 60, T0).getTicketNumber());
        assertEquals(3, entry.getNextTicketNumber());
    }

    @Test
    void fullParkReturnsNullAndKeepsTheTicket() {
        entry.enter(car("AAA001MC"), 60, T0);
        entry.enter(car("AAA002MC"), 60, T0);

        assertNull(entry.enter(car("AAA003MC"), 60, T0));
        assertEquals(3, entry.getNextTicketNumber());
        assertEquals(2, lane.size());
    }

    @Test
    void samePlateCannotEnterTwice() {
        entry.enter(car("AAA001MC"), 60, T0);
        assertThrows(IllegalStateException.class, () -> entry.enter(car("aaa-001-mc"), 60, T0));
        assertEquals(1, lane.size());
    }

    @Test
    void invalidDurationDoesNotTouchTheLane() {
        assertThrows(IllegalArgumentException.class, () -> entry.enter(car("AAA001MC"), 0, T0));
        assertEquals(0, lane.size());
        assertEquals(1, entry.getNextTicketNumber());
    }

    @Test
    void isParkedFindsByPlate() {
        entry.enter(car("AAA001MC"), 60, T0);
        assertTrue(entry.isParked(car("AAA 001 MC")));
        assertEquals(false, entry.isParked(car("ZZZ999MC")));
    }

    private static Vehicle car(String plate) {
        return new Vehicle(plate, Vehicle.Type.CAR, null);
    }
}
