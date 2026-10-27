package mz.uem.eda.parking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import mz.uem.eda.parking.service.policy.RoundRobin;
import mz.uem.eda.parking.service.policy.ServicePolicy;
import mz.uem.eda.parking.service.policy.StrictPriority;
import mz.uem.eda.parking.structures.LinkedQueue;
import mz.uem.eda.parking.structures.Queue;

class WaitingQueueServiceTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 10, 20, 8, 0);

    private Lane lane;
    private Queue<Lane> lanes;
    private EntryService entry;
    private ExitService exit;

    @BeforeEach
    void setUp() {
        lane = Lane.parking("L01", 2, StayClass.FULL_DAY);
        lanes = new LinkedQueue<>();
        lanes.enqueue(lane);
        lanes.enqueue(Lane.buffer("M", 10));
        entry = new EntryService(lanes, new FirstWithSpace());
        exit = new ExitService(lanes);
    }

    // ---- entrar na fila -------------------------------------------------

    @Test
    void theDeskChoosesTheQueue() {
        WaitingQueueService waiting = service(new StrictPriority());

        assertTrue(waiting.join(car("AAA001MC"), 60, T0, true).isPreferential());
        assertFalse(waiting.join(car("AAA002MC"), 60, T0, false).isPreferential());
        assertFalse(waiting.join(car("AAA003MC"), 60, T0).isPreferential());

        assertEquals(1, waiting.preferentialSize());
        assertEquals(2, waiting.normalSize());
    }

    @Test
    void sameCarCannotWaitTwice() {
        WaitingQueueService waiting = service(new StrictPriority());
        waiting.join(car("AAA001MC"), 60, T0);

        assertThrows(IllegalStateException.class,
                () -> waiting.join(car("aaa-001-mc"), 60, T0));
    }

    @Test
    void parkedCarCannotWait() {
        WaitingQueueService waiting = service(new StrictPriority());
        entry.enter(car("AAA001MC"), 60, T0);

        assertThrows(IllegalStateException.class,
                () -> waiting.join(car("AAA001MC"), 60, T0));
    }

    @Test
    void rejectsInvalidDuration() {
        WaitingQueueService waiting = service(new StrictPriority());
        assertThrows(IllegalArgumentException.class,
                () -> waiting.join(car("AAA001MC"), 0, T0));
    }

    // ---- atender --------------------------------------------------------

    @Test
    void nobodyEntersWhileTheParkIsFull() {
        WaitingQueueService waiting = service(new StrictPriority());
        fillThePark();
        waiting.join(car("BBB001MC"), 60, T0);

        assertNull(waiting.admitNext(T0.plusMinutes(5)));
        assertEquals(1, waiting.normalSize());
    }

    @Test
    void firstInTheQueueTakesTheFreedPlace() {
        WaitingQueueService waiting = service(new StrictPriority());
        fillThePark();
        waiting.join(car("BBB001MC"), 60, T0);
        waiting.join(car("BBB002MC"), 60, T0);

        exit.exitByPlate("AAA001MC", T0.plusHours(1));
        ParkingSession session = waiting.admitNext(T0.plusHours(1));

        assertEquals("BBB001MC", session.getVehicle().getPlate());
        assertEquals(T0.plusHours(1), session.getEntryTime());
        assertEquals(1, waiting.normalSize());
        assertTrue(entry.isParked(car("BBB001MC")));
    }

    @Test
    void returnsNullWhenNobodyIsWaiting() {
        assertNull(service(new StrictPriority()).admitNext(T0));
    }

    @Test
    void strictPriorityServesPreferentialFirst() {
        WaitingQueueService waiting = service(new StrictPriority());
        waiting.join(car("NNN001MC"), 60, T0);
        waiting.join(car("PPP001MC"), 60, T0.plusMinutes(1), true);

        assertEquals("PPP001MC", waiting.admitNext(T0.plusMinutes(2)).getVehicle().getPlate());
        assertEquals("NNN001MC", waiting.admitNext(T0.plusMinutes(2)).getVehicle().getPlate());
    }

    @Test
    void roundRobinLetsTheNormalQueueMove() {
        lane = Lane.parking("L01", 10, StayClass.FULL_DAY);
        lanes = new LinkedQueue<>();
        lanes.enqueue(lane);
        entry = new EntryService(lanes, new FirstWithSpace());
        WaitingQueueService waiting = new WaitingQueueService(lanes, entry, new RoundRobin(2));
        for (int i = 1; i <= 4; i++) {
            waiting.join(car("PPP00" + i + "MC"), 60, T0, true);
        }
        waiting.join(car("NNN001MC"), 60, T0);

        assertEquals("PPP001MC", plateOf(waiting.admitNext(T0)));
        assertEquals("PPP002MC", plateOf(waiting.admitNext(T0)));
        assertEquals("NNN001MC", plateOf(waiting.admitNext(T0)));
        assertEquals("PPP003MC", plateOf(waiting.admitNext(T0)));
    }

    @Test
    void contentsAreReadOnlyInArrivalOrder() {
        WaitingQueueService waiting = service(new StrictPriority());
        waiting.join(car("AAA001MC"), 60, T0);
        waiting.join(car("AAA002MC"), 60, T0);

        StringBuilder plates = new StringBuilder();
        for (WaitingCar car : waiting.normalContents()) {
            plates.append(car.getVehicle().getPlate()).append(' ');
        }
        assertEquals("AAA001MC AAA002MC ", plates.toString());
        assertEquals(2, waiting.normalSize());
    }

    // ---- desfazer a ida para a fila ---------------------------------------

    @Test
    void removeTakesACarOutOfTheMiddleKeepingTheOrder() {
        WaitingQueueService waiting = service(new StrictPriority());
        waiting.join(car("AAA001MC"), 60, T0);
        waiting.join(car("AAA002MC"), 60, T0);
        waiting.join(car("AAA003MC"), 60, T0);

        assertTrue(waiting.remove(car("AAA002MC")));
        assertFalse(waiting.remove(car("AAA002MC")));
        assertFalse(waiting.isWaiting(car("AAA002MC")));

        StringBuilder order = new StringBuilder();
        for (WaitingCar car : waiting.normalContents()) {
            order.append(car.getVehicle().getPlate()).append(' ');
        }
        assertEquals("AAA001MC AAA003MC ", order.toString());
    }

    // ---- auxiliares -----------------------------------------------------

    private WaitingQueueService service(ServicePolicy policy) {
        return new WaitingQueueService(lanes, entry, policy);
    }

    private void fillThePark() {
        entry.enter(car("AAA001MC"), 60, T0);
        entry.enter(car("AAA002MC"), 60, T0);
    }

    private static Vehicle car(String plate) {
        return new Vehicle(plate, Vehicle.Type.CAR);
    }

    private static String plateOf(ParkingSession session) {
        return session.getVehicle().getPlate();
    }
}
