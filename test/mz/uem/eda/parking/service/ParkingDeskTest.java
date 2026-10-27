package mz.uem.eda.parking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import mz.uem.eda.parking.model.Move;
import mz.uem.eda.parking.model.ParkingSession;
import mz.uem.eda.parking.model.Payment;
import mz.uem.eda.parking.model.Reservation;
import mz.uem.eda.parking.model.Tariff;
import mz.uem.eda.parking.model.User;
import mz.uem.eda.parking.model.Vehicle;
import mz.uem.eda.parking.persistence.ConnectionFactory;
import mz.uem.eda.parking.persistence.TestDatabase;
import mz.uem.eda.parking.service.policy.FirstWithSpace;
import mz.uem.eda.parking.service.policy.StrictPriority;

/**
 * A {@link ParkingDesk} contra uma base de dados verdadeira, acabada de criar
 * a partir de {@code sql/}. Cada teste "reabre o programa" criando uma
 * {@code ParkingDesk} nova sobre a mesma base, para confirmar que o que se
 * fez ficou gravado. Sem MySQL ligado, os testes são saltados.
 */
class ParkingDeskTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 10, 26, 8, 0);
    private static final char[] ADMIN_PASSWORD = "admin-pass".toCharArray();

    private ConnectionFactory db;

    @BeforeEach
    void freshDatabase() {
        db = TestDatabase.fresh();
    }

    @Test
    void firstStartCreatesTheAdminAndReopeningKeepsIt() throws SQLException {
        ParkingDesk desk = open();
        assertTrue(desk.needsFirstAdmin());

        User admin = desk.createFirstAdmin("admin", ADMIN_PASSWORD);
        assertEquals(admin, desk.getCurrentUser());

        ParkingDesk reopened = open();
        assertFalse(reopened.needsFirstAdmin());
        assertNull(reopened.login("admin", "errada-123".toCharArray()));
        assertNotNull(reopened.login("admin", ADMIN_PASSWORD));
    }

    @Test
    void operationsNeedSomeoneLoggedIn() throws SQLException {
        ParkingDesk desk = adminDesk();
        desk.logout();

        assertThrows(IllegalStateException.class,
                () -> desk.enter("AAA001MC", Vehicle.Type.CAR, 60, T0));
        assertThrows(IllegalStateException.class, () -> desk.findForExit("AAA001MC"));
    }

    @Test
    void entryShowsOnTheLaneAndSurvivesReopening() throws SQLException {
        ParkingDesk desk = adminDesk();
        ParkingSession session = desk.enter("AAA001MC", Vehicle.Type.CAR, 60, T0);

        assertEquals("C01", session.getLaneCode());
        assertEquals("AAA001MC", plates(lane(desk, "C01")));
        assertEquals(1, desk.occupiedPlaces());
        assertEquals(200, desk.totalPlaces());
        assertEquals("AAA001MC -> C01", lastMove(desk));

        ParkingDesk reopened = open();
        reopened.login("admin", ADMIN_PASSWORD);
        assertEquals("AAA001MC", plates(lane(reopened, "C01")));
        assertEquals(2, reopened.enter("AAA002MC", Vehicle.Type.CAR, 60, T0).getTicketNumber());
    }

    @Test
    void findForExitAcceptsPlateOrTicket() throws SQLException {
        ParkingDesk desk = adminDesk();
        ParkingSession session = desk.enter("AAA001MC", Vehicle.Type.CAR, 60, T0);

        assertEquals(session, desk.findForExit(" aaa-001 mc "));
        assertEquals(session, desk.findForExit("1"));
        assertNull(desk.findForExit("ZZZ999MC"));
        assertNull(desk.findForExit("42"));
    }

    @Test
    void exitChargesTheQuoteRestoresTheOrderAndSaves() throws SQLException {
        ParkingDesk desk = adminDesk();
        desk.enter("AAA001MC", Vehicle.Type.CAR, 60, T0);
        desk.enter("AAA002MC", Vehicle.Type.CAR, 60, T0);
        desk.enter("AAA003MC", Vehicle.Type.CAR, 60, T0);

        LocalDateTime at = T0.plusHours(2);
        ParkingSession first = desk.findForExit("AAA001MC");
        long quoted = desk.quote(first, at);
        ExitResult result = desk.exit(first, Payment.Method.MPESA, at);

        assertTrue(quoted > 0);
        assertEquals(quoted, result.getPayment().getAmountCents());
        assertEquals(4, result.getPlan().getManoeuvreCount());     // 2 carros por cima: 2k
        assertEquals(5, result.getPlan().getTotalMoves());
        assertNull(result.getAdmitted());
        assertFalse(first.isActive());
        assertEquals("AAA003MC AAA002MC", plates(lane(desk, "C01")));
        assertEquals(0, lane(desk, "M").size());
        assertThrows(IllegalStateException.class, () -> desk.exit(first, Payment.Method.CASH, at));

        ParkingDesk reopened = open();
        reopened.login("admin", ADMIN_PASSWORD);
        assertEquals("AAA003MC AAA002MC", plates(lane(reopened, "C01")));
        assertNull(reopened.findForExit("AAA001MC"));
        assertEquals(1, TestDatabase.count(db, "SELECT COUNT(*) FROM payments WHERE method = 'MPESA'"));
    }

    @Test
    void fullParkQueuesTheCarAndTheNextExitLetsItIn() throws SQLException {
        ParkingDesk desk = adminDesk();
        for (int i = 1; i <= desk.totalPlaces(); i++) {
            assertNotNull(desk.enter(String.format("CAR%03dMC", i), Vehicle.Type.CAR, 60, T0));
        }

        assertNull(desk.enter("WAIT01MC", Vehicle.Type.CAR, 60, T0));
        assertEquals("WAIT01MC", desk.normalQueue().iterator().next().getVehicle().getPlate());
        assertEquals(1, desk.normalWaitingCount());
        assertEquals(0, desk.preferentialWaitingCount());
        assertThrows(IllegalStateException.class,
                () -> desk.enter("WAIT01MC", Vehicle.Type.CAR, 60, T0));

        ParkingSession leaving = desk.findForExit("CAR005MC");    // topo da C01
        ExitResult result = desk.exit(leaving, Payment.Method.CASH, T0.plusMinutes(30));

        assertEquals(0, result.getPlan().getManoeuvreCount());
        assertEquals("WAIT01MC", result.getAdmitted().getVehicle().getPlate());
        assertFalse(desk.normalQueue().iterator().hasNext());
        assertEquals(0, desk.normalWaitingCount());
        assertEquals("WAIT01MC -> C01", lastMove(desk));
        assertEquals(desk.totalPlaces(), TestDatabase.count(db,
                "SELECT COUNT(*) FROM parking_sessions WHERE status = 'ACTIVE'"));
    }

    @Test
    void reservedCarChecksInWithTheReservedDuration() throws SQLException {
        ParkingDesk desk = adminDesk();
        Reservation reservation = desk.reserve("RES001MC", 90, T0);
        assertNotNull(reservation);
        assertEquals(199, desk.freeForWalkIns(T0));

        ParkingSession session = desk.enter("RES001MC", Vehicle.Type.CAR, 5, T0.plusMinutes(10));

        assertEquals(90, session.getEstimatedMinutes());
        assertEquals(Reservation.Status.CHECKED_IN, reservation.getStatus());
        assertEquals(1, TestDatabase.count(db, "SELECT COUNT(*) FROM reservations WHERE status = 'CHECKED_IN'"));
    }

    @Test
    void onlyAdminsManageUsersAndTheTariff() throws SQLException {
        ParkingDesk desk = adminDesk();
        desk.registerUser("ana", "operadora-1".toCharArray(), User.Role.OPERATOR);

        desk.login("ana", "operadora-1".toCharArray());
        assertThrows(SecurityException.class,
                () -> desk.registerUser("rui", "operador-2".toCharArray(), User.Role.OPERATOR));
        assertThrows(SecurityException.class,
                () -> desk.changeTariff(new Tariff(0, 60, 10000, 50000), T0));

        desk.login("admin", ADMIN_PASSWORD);
        desk.changeTariff(new Tariff(0, 60, 10000, 50000), T0);
        assertEquals(10000, desk.getTariff().getHourlyRateCents());
        assertTrue(desk.removeUser("ana"));

        ParkingDesk reopened = open();
        assertEquals(10000, reopened.getTariff().getHourlyRateCents());
        assertNull(reopened.login("ana", "operadora-1".toCharArray()));
    }

    @Test
    void priceWithPreviewsATariffWithoutChangingIt() throws SQLException {
        ParkingDesk desk = adminDesk();
        Tariff active = desk.getTariff();
        // O exemplo do BillingService: 15 min grátis, fracções de 15 min, 60 MZN/h, tecto de 400 MZN.
        Tariff preview = new Tariff(15, 15, 6000, 40000);

        assertEquals(0, desk.priceWith(preview, 10));
        assertEquals(7500, desk.priceWith(preview, 61));
        assertEquals(40000, desk.priceWith(preview, 10 * 60));
        assertEquals(46000, desk.priceWith(preview, 25 * 60));
        assertEquals(active, desk.getTariff());
    }

    @Test
    void recentMovesKeepOnlyTheLastTwenty() throws SQLException {
        ParkingDesk desk = adminDesk();
        for (int i = 1; i <= ParkingDesk.RECENT_MOVES + 5; i++) {
            desk.enter(String.format("CAR%03dMC", i), Vehicle.Type.CAR, 60, T0);
        }

        int count = 0;
        Move oldest = null;
        for (Move move : desk.recentMoves()) {
            if (oldest == null) {
                oldest = move;
            }
            count++;
        }
        assertEquals(ParkingDesk.RECENT_MOVES, count);
        assertEquals("CAR006MC", oldest.getPlate());
    }

    // ------------------------------------------------------------------

    private ParkingDesk open() throws SQLException {
        return new ParkingDesk(db, new FirstWithSpace(), new StrictPriority(), T0);
    }

    private ParkingDesk adminDesk() throws SQLException {
        ParkingDesk desk = open();
        desk.createFirstAdmin("admin", ADMIN_PASSWORD);
        return desk;
    }

    private static LaneView lane(ParkingDesk desk, String code) {
        for (LaneView lane : desk.lanes()) {
            if (lane.getCode().equals(code)) {
                return lane;
            }
        }
        throw new AssertionError("baia inexistente: " + code);
    }

    /** As matrículas da baia, do topo para o fundo, separadas por espaços. */
    private static String plates(LaneView lane) {
        StringBuilder text = new StringBuilder();
        for (ParkingSession session : lane.sessions()) {
            if (text.length() > 0) {
                text.append(' ');
            }
            text.append(session.getVehicle().getPlate());
        }
        return text.toString();
    }

    private static String lastMove(ParkingDesk desk) {
        Move last = null;
        for (Move move : desk.recentMoves()) {
            last = move;
        }
        return last.getPlate() + " -> " + last.getToLaneCode();
    }
}
