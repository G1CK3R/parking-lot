package mz.uem.eda.parking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import mz.uem.eda.parking.model.Customer;
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
    private static final Customer VIP = new Customer("Ana", null, StayClass.FULL_DAY, true);
    private static final Customer REGULAR = new Customer("Rui", null, StayClass.FULL_DAY, false);

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
    void preferentialCustomersGoToThePreferentialQueue() {
        WaitingQueueService waiting = service(new StrictPriority());

        assertTrue(waiting.join(car("AAA001MC", VIP), 60, T0).isPreferential());
        assertFalse(waiting.join(car("AAA002MC", REGULAR), 60, T0).isPreferential());
        assertFalse(waiting.join(car("AAA003MC", null), 60, T0).isPreferential());

        assertEquals(1, waiting.preferentialSize());
        assertEquals(2, waiting.normalSize());
    }

    @Test
    void sameCarCannotWaitTwice() {
        WaitingQueueService waiting = service(new StrictPriority());
        waiting.join(car("AAA001MC", null), 60, T0);

        assertThrows(IllegalStateException.class,
                () -> waiting.join(car("aaa-001-mc", null), 60, T0));
    }

    @Test
    void parkedCarCannotWait() {
        WaitingQueueService waiting = service(new StrictPriority());
        entry.enter(car("AAA001MC", null), 60, T0);

        assertThrows(IllegalStateException.class,
                () -> waiting.join(car("AAA001MC", null), 60, T0));
    }

    @Test
    void rejectsInvalidDuration() {
        WaitingQueueService waiting = service(new StrictPriority());
        assertThrows(IllegalArgumentException.class,
                () -> waiting.join(car("AAA001MC", null), 0, T0));
    }

    // ---- atender --------------------------------------------------------

    @Test
    void nobodyEntersWhileTheParkIsFull() {
        WaitingQueueService waiting = service(new StrictPriority());
        fillThePark();
        waiting.join(car("BBB001MC", null), 60, T0);

        assertNull(waiting.admitNext(T0.plusMinutes(5)));
        assertEquals(1, waiting.normalSize());
    }

    @Test
    void firstInTheQueueTakesTheFreedPlace() {
        WaitingQueueService waiting = service(new StrictPriority());
        fillThePark();
        waiting.join(car("BBB001MC", null), 60, T0);
        waiting.join(car("BBB002MC", null), 60, T0);

        exit.exitByPlate("AAA001MC", T0.plusHours(1));
        ParkingSession session = waiting.admitNext(T0.plusHours(1));

        assertEquals("BBB001MC", session.getVehicle().getPlate());
        assertEquals(T0.plusHours(1), session.getEntryTime());
        assertEquals(1, waiting.normalSize());
        assertTrue(entry.isParked(car("BBB001MC", null)));
    }

    @Test
    void returnsNullWhenNobodyIsWaiting() {
        assertNull(service(new StrictPriority()).admitNext(T0));
    }

    @Test
    void strictPriorityServesPreferentialFirst() {
        WaitingQueueService waiting = service(new StrictPriority());
        waiting.join(car("NNN001MC", REGULAR), 60, T0);
        waiting.join(car("PPP001MC", VIP), 60, T0.plusMinutes(1));

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
            waiting.join(car("PPP00" + i + "MC", VIP), 60, T0);
        }
        waiting.join(car("NNN001MC", REGULAR), 60, T0);

        assertEquals("PPP001MC", plateOf(waiting.admitNext(T0)));
        assertEquals("PPP002MC", plateOf(waiting.admitNext(T0)));
        assertEquals("NNN001MC", plateOf(waiting.admitNext(T0)));
        assertEquals("PPP003MC", plateOf(waiting.admitNext(T0)));
    }

    @Test
    void contentsAreReadOnlyInArrivalOrder() {
        WaitingQueueService waiting = service(new StrictPriority());
        waiting.join(car("AAA001MC", null), 60, T0);
        waiting.join(car("AAA002MC", null), 60, T0);

        StringBuilder plates = new StringBuilder();
        for (WaitingCar car : waiting.normalContents()) {
            plates.append(car.getVehicle().getPlate()).append(' ');
        }
        assertEquals("AAA001MC AAA002MC ", plates.toString());
        assertEquals(2, waiting.normalSize());
    }

    // ---- auxiliares -----------------------------------------------------

    private WaitingQueueService service(ServicePolicy policy) {
        return new WaitingQueueService(lanes, entry, policy);
    }

    private void fillThePark() {
        entry.enter(car("AAA001MC", null), 60, T0);
        entry.enter(car("AAA002MC", null), 60, T0);
    }

    private static Vehicle car(String plate, Customer customer) {
        return new Vehicle(plate, Vehicle.Type.CAR, customer);
    }

    private static String plateOf(ParkingSession session) {
        return session.getVehicle().getPlate();
    }
}
