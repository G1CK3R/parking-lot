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
import mz.uem.eda.parking.model.Move;
import mz.uem.eda.parking.model.ParkingSession;
import mz.uem.eda.parking.model.RetrievalPlan;
import mz.uem.eda.parking.model.StayClass;
import mz.uem.eda.parking.model.Vehicle;
import mz.uem.eda.parking.structures.LinkedQueue;
import mz.uem.eda.parking.structures.Queue;

class ExitServiceTest {

    private static final LocalDateTime ENTRY = LocalDateTime.of(2026, 10, 19, 8, 0);
    private static final LocalDateTime EXIT = ENTRY.plusHours(3);

    private Lane lane;
    private Lane other;
    private Lane buffer;
    private ExitService service;
    private int ticket;

    @BeforeEach
    void setUp() {
        lane = Lane.parking("L01", 5, StayClass.FULL_DAY);
        other = Lane.parking("L02", 5, StayClass.FULL_DAY);
        buffer = Lane.buffer("M", 10);
        service = new ExitService(lanes(lane, other, buffer));
        ticket = 1;
        // fundo -> topo: AAA001 ... AAA005; AAA005 está no topo (profundidade 0)
        for (int i = 1; i <= 5; i++) {
            park(lane, "AAA00" + i + "MC");
        }
    }

    // ---- a porta de 1 de Novembro -------------------------------------

    @Test
    void restoresTheLaneAndCountsTwoKManoeuvres() {
        RetrievalPlan plan = service.exitByPlate("AAA003MC", EXIT);   // profundidade 2

        assertEquals(2, plan.getDepth());
        assertEquals(2 * plan.getDepth(), plan.getManoeuvreCount());
        assertEquals(2 * plan.getDepth() + 1, plan.getTotalMoves());
        assertEquals("AAA005MC AAA004MC AAA002MC AAA001MC", platesOf(lane));
        assertTrue(buffer.isEmpty());
    }

    @Test
    void holdsForEveryDepth() {
        for (int depth = 0; depth < 5; depth++) {
            setUp();
            String plate = "AAA00" + (5 - depth) + "MC";
            RetrievalPlan plan = service.exitByPlate(plate, EXIT);
            assertEquals(depth, plan.getDepth());
            assertEquals(2 * depth, plan.getManoeuvreCount());
            assertEquals(4, lane.size());
            assertTrue(buffer.isEmpty());
        }
    }

    // ---- o plano --------------------------------------------------------

    @Test
    void planListsTheMovesInOrder() {
        RetrievalPlan plan = service.exitByPlate("AAA003MC", EXIT);

        assertEquals("1: AAA005MC L01 -> M", plan.nextMove().toString());
        assertEquals("2: AAA004MC L01 -> M", plan.nextMove().toString());
        Move exit = plan.nextMove();
        assertEquals("3: AAA003MC L01 -> OUT", exit.toString());
        assertTrue(exit.isExit());
        assertEquals("4: AAA004MC M -> L01", plan.nextMove().toString());
        assertEquals("5: AAA005MC M -> L01", plan.nextMove().toString());
        assertFalse(plan.hasMove());
    }

    @Test
    void topCarLeavesWithoutManoeuvres() {
        RetrievalPlan plan = service.exitByPlate("AAA005MC", EXIT);

        assertEquals(0, plan.getManoeuvreCount());
        assertEquals(1, plan.getTotalMoves());
        assertEquals("AAA004MC AAA003MC AAA002MC AAA001MC", platesOf(lane));
    }

    @Test
    void closesTheSessionWithTheManoeuvres() {
        RetrievalPlan plan = service.exitByPlate("AAA002MC", EXIT);   // profundidade 3
        ParkingSession session = plan.getTarget();

        assertFalse(session.isActive());
        assertEquals(EXIT, session.getExitTime());
        assertEquals(6, session.getManoeuvres());
        assertEquals("AAA002MC", session.getVehicle().getPlate());
    }

    // ---- a busca --------------------------------------------------------

    @Test
    void findsByTicket() {
        RetrievalPlan plan = service.exitByTicket(1, EXIT);   // AAA001MC, no fundo

        assertEquals("AAA001MC", plan.getTarget().getVehicle().getPlate());
        assertEquals(4, plan.getDepth());
        assertEquals(8, plan.getManoeuvreCount());
    }

    @Test
    void normalizesThePlateTypedByTheOperator() {
        RetrievalPlan plan = service.exitByPlate("aaa-003 mc", EXIT);

        assertEquals("AAA003MC", plan.getTarget().getVehicle().getPlate());
    }

    @Test
    void findsCarsInOtherLanes() {
        park(other, "BBB001MC");
        RetrievalPlan plan = service.exitByPlate("BBB001MC", EXIT);

        assertEquals("L02", plan.nextMove().getFromLaneCode());
        assertTrue(other.isEmpty());
        assertEquals(5, lane.size());
    }

    @Test
    void returnsNullWhenTheCarIsNotParked() {
        assertNull(service.exitByPlate("ZZZ999MC", EXIT));
        assertNull(service.exitByTicket(99, EXIT));
        assertEquals(5, lane.size());
    }

    @Test
    void sameCarCannotLeaveTwice() {
        service.exitByPlate("AAA003MC", EXIT);
        assertNull(service.exitByPlate("AAA003MC", EXIT));
    }

    // ---- procurar sem tirar ---------------------------------------------

    @Test
    void findsWithoutTakingTheCarOut() {
        ParkingSession byPlate = service.findByPlate("aaa 002 mc");
        ParkingSession byTicket = service.findByTicket(2);

        assertEquals("AAA002MC", byPlate.getVehicle().getPlate());
        assertEquals(byPlate, byTicket);
        assertTrue(byPlate.isActive());
        assertEquals("AAA005MC AAA004MC AAA003MC AAA002MC AAA001MC", platesOf(lane));
    }

    @Test
    void findReturnsNullForCarsNotParked() {
        assertNull(service.findByPlate("ZZZ999MC"));
        assertNull(service.findByTicket(99));
        service.exitByPlate("AAA003MC", EXIT);
        assertNull(service.findByPlate("AAA003MC"));
    }

    // ---- erros que não mexem no parque ----------------------------------

    @Test
    void rejectsExitBeforeEntryWithoutTouchingTheLane() {
        assertThrows(IllegalArgumentException.class,
                () -> service.exitByPlate("AAA003MC", ENTRY.minusMinutes(1)));
        assertEquals("AAA005MC AAA004MC AAA003MC AAA002MC AAA001MC", platesOf(lane));
        assertTrue(buffer.isEmpty());
    }

    @Test
    void rejectsWhenTheBufferHasNoRoomWithoutTouchingTheLane() {
        Lane small = Lane.buffer("M", 2);
        ExitService tight = new ExitService(lanes(lane, small));

        assertThrows(IllegalStateException.class, () -> tight.exitByPlate("AAA002MC", EXIT));
        assertEquals("AAA005MC AAA004MC AAA003MC AAA002MC AAA001MC", platesOf(lane));
        assertTrue(small.isEmpty());
    }

    @Test
    void requiresExactlyOneBuffer() {
        assertThrows(IllegalArgumentException.class, () -> new ExitService(lanes(lane, other)));
        assertThrows(IllegalArgumentException.class,
                () -> new ExitService(lanes(lane, buffer, Lane.buffer("M2", 10))));
    }

    // ---- auxiliares -----------------------------------------------------

    private void park(Lane target, String plate) {
        Vehicle vehicle = new Vehicle(plate, Vehicle.Type.CAR);
        target.asStack().push(new ParkingSession(ticket, vehicle, target.getCode(),
                StayClass.FULL_DAY, 180, ENTRY));
        ticket++;
    }

    private static Queue<Lane> lanes(Lane... all) {
        Queue<Lane> queue = new LinkedQueue<>();
        for (Lane lane : all) {
            queue.enqueue(lane);
        }
        return queue;
    }

    /** As matrículas da baia, do topo para o fundo. */
    private static String platesOf(Lane lane) {
        StringBuilder text = new StringBuilder();
        for (ParkingSession session : lane.contents()) {
            if (text.length() > 0) {
                text.append(' ');
            }
            text.append(session.getVehicle().getPlate());
        }
        return text.toString();
    }
}
