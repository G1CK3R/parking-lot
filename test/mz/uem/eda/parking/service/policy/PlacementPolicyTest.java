package mz.uem.eda.parking.service.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import mz.uem.eda.parking.model.Lane;
import mz.uem.eda.parking.model.ParkingSession;
import mz.uem.eda.parking.model.StayClass;
import mz.uem.eda.parking.model.Vehicle;
import mz.uem.eda.parking.structures.LinkedQueue;
import mz.uem.eda.parking.structures.Queue;

class PlacementPolicyTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 10, 20, 8, 0);
    private int ticket = 1;

    // ---- LeastOccupied --------------------------------------------------

    @Test
    void leastOccupiedChoosesTheLaneWithFewestCars() {
        Lane a = Lane.parking("A", 10, StayClass.FULL_DAY);
        Lane b = Lane.parking("B", 10, StayClass.FULL_DAY);
        Lane c = Lane.parking("C", 10, StayClass.FULL_DAY);
        park(a, StayClass.FULL_DAY, 3);
        park(b, StayClass.FULL_DAY, 1);
        park(c, StayClass.FULL_DAY, 2);

        assertSame(b, new LeastOccupied().chooseLane(lanes(a, b, c), StayClass.SHORT));
    }

    @Test
    void leastOccupiedCountsCarsNotPercentage() {
        Lane deep = Lane.parking("A", 10, StayClass.FULL_DAY);   // 3/10 = 30 %
        Lane shallow = Lane.parking("B", 5, StayClass.SHORT);    // 2/5 = 40 %, mas 2 carros
        park(deep, StayClass.FULL_DAY, 3);
        park(shallow, StayClass.SHORT, 2);

        assertSame(shallow, new LeastOccupied().chooseLane(lanes(deep, shallow), StayClass.FULL_DAY));
    }

    @Test
    void leastOccupiedBreaksTiesByConfigurationOrder() {
        Lane a = Lane.parking("A", 5, StayClass.FULL_DAY);
        Lane b = Lane.parking("B", 5, StayClass.FULL_DAY);
        assertSame(a, new LeastOccupied().chooseLane(lanes(a, b), StayClass.FULL_DAY));
    }

    @Test
    void leastOccupiedSkipsFullLanesAndTheBuffer() {
        Lane buffer = Lane.buffer("M", 10);
        Lane full = Lane.parking("A", 1, StayClass.FULL_DAY);
        Lane b = Lane.parking("B", 5, StayClass.FULL_DAY);
        park(full, StayClass.FULL_DAY, 1);
        park(b, StayClass.FULL_DAY, 3);

        assertSame(b, new LeastOccupied().chooseLane(lanes(buffer, full, b), StayClass.FULL_DAY));
        park(b, StayClass.FULL_DAY, 2);
        assertNull(new LeastOccupied().chooseLane(lanes(buffer, full, b), StayClass.FULL_DAY));
    }

    // ---- ByStayClass: passo 1, a sua classe -----------------------------

    @Test
    void byStayClassPrefersALaneOfItsOwnClass() {
        Lane full = Lane.parking("J", 10, StayClass.FULL_DAY);
        Lane shortLane = Lane.parking("C", 5, StayClass.SHORT);

        assertSame(shortLane, new ByStayClass().chooseLane(lanes(full, shortLane), StayClass.SHORT));
        assertSame(full, new ByStayClass().chooseLane(lanes(full, shortLane), StayClass.FULL_DAY));
    }

    @Test
    void byStayClassStacksOnTheSameClass() {
        Lane shortLane = Lane.parking("C", 5, StayClass.SHORT);
        park(shortLane, StayClass.SHORT, 3);

        assertSame(shortLane, new ByStayClass().chooseLane(lanes(shortLane), StayClass.SHORT));
    }

    // ---- ByStayClass: passo 2, transbordo -------------------------------

    @Test
    void byStayClassOverflowsShortOnTopOfFullDay() {
        Lane shortLane = Lane.parking("C", 1, StayClass.SHORT);
        Lane full = Lane.parking("J", 10, StayClass.FULL_DAY);
        park(shortLane, StayClass.SHORT, 1);          // cheia
        park(full, StayClass.FULL_DAY, 4);            // topo sai depois

        assertSame(full, new ByStayClass().chooseLane(lanes(shortLane, full), StayClass.SHORT));
    }

    @Test
    void byStayClassNeverPutsALaterCarOnTopOfAnEarlierOne() {
        Lane full = Lane.parking("J", 1, StayClass.FULL_DAY);
        Lane shortLane = Lane.parking("C", 5, StayClass.SHORT);
        Lane empty = Lane.parking("L", 5, StayClass.LONG_TERM);
        park(full, StayClass.FULL_DAY, 1);            // cheia
        park(shortLane, StayClass.SHORT, 1);          // topo sai antes: não serve

        // passa por cima da baia curta e vai para a vazia
        assertSame(empty, new ByStayClass().chooseLane(lanes(full, shortLane, empty), StayClass.FULL_DAY));
    }

    @Test
    void byStayClassLooksAtTheTopCarNotTheLaneClass() {
        Lane full = Lane.parking("J", 10, StayClass.FULL_DAY);
        park(full, StayClass.FULL_DAY, 2);
        park(full, StayClass.SHORT, 1);               // transbordo antigo no topo
        Lane other = Lane.parking("K", 10, StayClass.FULL_DAY);
        park(other, StayClass.FULL_DAY, 5);

        assertSame(other, new ByStayClass().chooseLane(lanes(full, other), StayClass.FULL_DAY));
    }

    // ---- ByStayClass: passo 3, último recurso ---------------------------

    @Test
    void byStayClassBreaksTheRuleRatherThanLeaveTheCarWaiting() {
        Lane a = Lane.parking("C", 5, StayClass.SHORT);
        Lane b = Lane.parking("J", 5, StayClass.FULL_DAY);
        park(a, StayClass.SHORT, 1);
        park(b, StayClass.SHORT, 1);

        // nenhuma baia cumpre a regra para LONG_TERM; fica a primeira com espaço
        assertSame(a, new ByStayClass().chooseLane(lanes(a, b), StayClass.LONG_TERM));
        // para FULL_DAY, a primeira da sua classe
        assertSame(b, new ByStayClass().chooseLane(lanes(a, b), StayClass.FULL_DAY));
    }

    @Test
    void byStayClassReturnsNullWhenEveryLaneIsFull() {
        Lane a = Lane.parking("C", 1, StayClass.SHORT);
        park(a, StayClass.SHORT, 1);
        assertNull(new ByStayClass().chooseLane(lanes(a, Lane.buffer("M", 10)), StayClass.SHORT));
        assertNull(new ByStayClass().chooseLane(new LinkedQueue<Lane>(), StayClass.SHORT));
    }

    @Test
    void byStayClassRequiresTheStayClass() {
        Lane a = Lane.parking("C", 1, StayClass.SHORT);
        assertThrows(NullPointerException.class, () -> new ByStayClass().chooseLane(lanes(a), null));
    }

    @Test
    void byStayClassDoesNotTouchTheLanes() {
        Lane a = Lane.parking("J", 5, StayClass.FULL_DAY);
        park(a, StayClass.FULL_DAY, 3);
        new ByStayClass().chooseLane(lanes(a), StayClass.SHORT);
        assertEquals(3, a.size());
    }

    // ---- auxiliares -----------------------------------------------------

    private void park(Lane lane, StayClass stayClass, int count) {
        for (int i = 0; i < count; i++) {
            Vehicle vehicle = new Vehicle("CAR" + ticket, Vehicle.Type.CAR, null);
            lane.asStack().push(new ParkingSession(ticket, vehicle, lane.getCode(), stayClass, 60, T0));
            ticket++;
        }
    }

    private static Queue<Lane> lanes(Lane... all) {
        Queue<Lane> queue = new LinkedQueue<>();
        for (Lane lane : all) {
            queue.enqueue(lane);
        }
        return queue;
    }
}
