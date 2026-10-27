package mz.uem.eda.parking.service.policy;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import mz.uem.eda.parking.model.Lane;
import mz.uem.eda.parking.model.ParkingSession;
import mz.uem.eda.parking.model.StayClass;
import mz.uem.eda.parking.model.Vehicle;
import mz.uem.eda.parking.structures.LinkedQueue;
import mz.uem.eda.parking.structures.Queue;

class FirstWithSpaceTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 10, 18, 8, 0);

    private final PlacementPolicy policy = new FirstWithSpace();
    private int ticket = 1;

    @Test
    void choosesFirstLaneWhenItHasSpace() {
        Lane a = Lane.parking("A", 2, StayClass.FULL_DAY);
        Lane b = Lane.parking("B", 2, StayClass.SHORT);
        assertSame(a, policy.chooseLane(lanes(a, b), StayClass.SHORT));
    }

    @Test
    void skipsFullLanes() {
        Lane a = Lane.parking("A", 1, StayClass.FULL_DAY);
        Lane b = Lane.parking("B", 2, StayClass.FULL_DAY);
        park(a);
        assertSame(b, policy.chooseLane(lanes(a, b), StayClass.FULL_DAY));
    }

    @Test
    void neverChoosesTheBuffer() {
        Lane buffer = Lane.buffer("M", 10);
        Lane a = Lane.parking("A", 2, StayClass.FULL_DAY);
        assertSame(a, policy.chooseLane(lanes(buffer, a), StayClass.FULL_DAY));
    }

    @Test
    void returnsNullWhenEveryLaneIsFull() {
        Lane a = Lane.parking("A", 1, StayClass.FULL_DAY);
        Lane buffer = Lane.buffer("M", 10);
        park(a);
        assertNull(policy.chooseLane(lanes(a, buffer), StayClass.FULL_DAY));
    }

    @Test
    void returnsNullWithoutLanes() {
        assertNull(policy.chooseLane(new LinkedQueue<Lane>(), StayClass.SHORT));
    }

    // ---- auxiliares -----------------------------------------------------

    private void park(Lane lane) {
        Vehicle vehicle = new Vehicle("CAR" + ticket, Vehicle.Type.CAR);
        lane.asStack().push(new ParkingSession(ticket, vehicle, lane.getCode(),
                StayClass.FULL_DAY, 60, T0));
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
