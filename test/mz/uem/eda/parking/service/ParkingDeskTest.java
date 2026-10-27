package mz.uem.eda.parking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.YearMonth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import mz.uem.eda.parking.model.AuditEntry;
import mz.uem.eda.parking.model.MonthlyPass;
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
    void aCancelledReservationFreesThePlaceAndStaysCancelledAfterReopening() throws SQLException {
        ParkingDesk desk = adminDesk();
        desk.reserve("RES001MC", 90, T0);
        assertEquals(1, desk.activeReservationCount());

        assertTrue(desk.cancelReservation("res-001-mc", T0.plusMinutes(5)));
        assertFalse(desk.cancelReservation("RES001MC", T0.plusMinutes(6)));
        assertEquals(0, desk.activeReservationCount());
        assertEquals(200, desk.freeForWalkIns(T0.plusMinutes(6)));
        assertEquals(1, TestDatabase.count(db, "SELECT COUNT(*) FROM reservations WHERE status = 'CANCELLED'"));

        ParkingDesk reopened = open();
        assertEquals(0, reopened.activeReservationCount());
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
    void reportsCoverEveryoneAndTheCashCloseOnlyTheOperator() throws SQLException {
        ParkingDesk desk = adminDesk();
        desk.registerUser("ana", "operadora-1".toCharArray(), User.Role.OPERATOR);
        desk.enter("AAA001MC", Vehicle.Type.CAR, 60, T0);
        desk.enter("AAA002MC", Vehicle.Type.CAR, 60, T0);
        desk.exit(desk.findForExit("AAA002MC"), Payment.Method.CASH, T0.plusHours(2));

        desk.login("ana", "operadora-1".toCharArray());
        desk.exit(desk.findForExit("AAA001MC"), Payment.Method.MPESA, T0.plusHours(3));
        LocalDateTime dayEnd = T0.plusDays(1);
        CashClose close = desk.cashClose(T0, dayEnd);
        assertEquals(1, close.getReport().getCount());
        assertEquals("ana", close.getReport().getOperator());
        assertThrows(SecurityException.class, () -> desk.revenue(T0, dayEnd));

        desk.login("admin", ADMIN_PASSWORD);
        assertEquals(2, desk.revenue(T0, dayEnd).getCount());

        // Depois de reabrir, o histórico vem da base, com o operador de cada pagamento.
        ParkingDesk reopened = open();
        reopened.login("admin", ADMIN_PASSWORD);
        RevenueReport report = reopened.revenue(T0, dayEnd);
        assertEquals(2, report.getCount());
        int operators = 0;
        for (RevenueLine<String> line : report.byOperator()) {
            operators++;
            assertEquals(1, line.getCount());
        }
        assertEquals(2, operators);
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
    // Desfazer
    // ------------------------------------------------------------------

    @Test
    void undoTakesTheLastCarOutAndVoidsItsTicket() throws SQLException {
        ParkingDesk desk = adminDesk();
        assertFalse(desk.canUndo());
        desk.enter("AAA001MC", Vehicle.Type.CAR, 60, T0);
        ParkingSession second = desk.enter("AAA002MC", Vehicle.Type.CAR, 60, T0);

        assertTrue(desk.canUndo());
        assertEquals(Operation.Type.ENTRY, desk.lastOperation().getType());
        assertEquals("AAA002MC", desk.lastOperation().getPlate());

        Operation undone = desk.undo(T0.plusMinutes(1));
        assertEquals(second, undone.getSession());
        assertEquals(ParkingSession.Status.VOIDED, second.getStatus());
        assertEquals("AAA001MC", plates(lane(desk, "C01")));
        assertEquals("AAA002MC -> null", lastMove(desk));

        desk.undo(T0.plusMinutes(2));
        assertEquals("", plates(lane(desk, "C01")));
        assertFalse(desk.canUndo());
        assertThrows(IllegalStateException.class, () -> desk.undo(T0.plusMinutes(3)));
        assertEquals(2, TestDatabase.count(db, "SELECT COUNT(*) FROM parking_sessions WHERE status = 'VOIDED'"));
        assertEquals(2, count(desk.auditLog(T0, T0.plusDays(1), null, AuditEntry.Action.ENTRY_UNDONE)));

        // O talão anulado não volta a ser usado, e a baia fica vazia depois de reabrir.
        ParkingDesk reopened = open();
        reopened.login("admin", ADMIN_PASSWORD, T0);
        assertEquals("", plates(lane(reopened, "C01")));
        assertEquals(3, reopened.enter("AAA003MC", Vehicle.Type.CAR, 60, T0).getTicketNumber());
    }

    @Test
    void undoCancelsAReservationAndBringsBackACancelledOne() throws SQLException {
        ParkingDesk desk = adminDesk();
        Reservation made = desk.reserve("RES001MC", 60, T0);
        assertEquals(Operation.Type.RESERVATION, desk.lastOperation().getType());
        desk.undo(T0.plusMinutes(1));
        assertEquals(Reservation.Status.CANCELLED, made.getStatus());
        assertEquals(0, desk.activeReservationCount());

        Reservation kept = desk.reserve("RES002MC", 60, T0.plusMinutes(2));
        desk.cancelReservation("RES002MC", T0.plusMinutes(3));
        assertEquals(Operation.Type.CANCELLATION, desk.lastOperation().getType());
        desk.undo(T0.plusMinutes(4));
        assertEquals(Reservation.Status.ACTIVE, kept.getStatus());
        assertEquals(1, desk.activeReservationCount());
        assertEquals(1, TestDatabase.count(db, "SELECT COUNT(*) FROM reservations WHERE status = 'ACTIVE'"));

        ParkingDesk reopened = open();
        assertEquals(1, reopened.activeReservationCount());
    }

    @Test
    void anExitOrANewLoginEmptiesTheUndoStack() throws SQLException {
        ParkingDesk desk = adminDesk();
        desk.enter("AAA001MC", Vehicle.Type.CAR, 60, T0);
        desk.exit(desk.findForExit("AAA001MC"), Payment.Method.CASH, T0.plusHours(1));
        assertFalse(desk.canUndo());

        desk.enter("AAA002MC", Vehicle.Type.CAR, 60, T0);
        desk.login("admin", ADMIN_PASSWORD, T0);
        assertFalse(desk.canUndo());
        assertNull(desk.lastOperation());
    }

    @Test
    void queuedCarsCanBeUndoneAndACancellationLetsTheNextOneIn() throws SQLException {
        ParkingDesk desk = adminDesk();
        for (int i = 1; i < desk.totalPlaces(); i++) {
            desk.enter(String.format("CAR%03dMC", i), Vehicle.Type.CAR, 60, T0);
        }
        desk.reserve("RES001MC", 60, T0);
        assertEquals(0, desk.freeForWalkIns(T0));

        assertNull(desk.enter("WAIT01MC", Vehicle.Type.CAR, 60, T0));
        assertNull(desk.enter("WAIT02MC", Vehicle.Type.CAR, 60, true, T0));
        assertEquals(1, desk.normalWaitingCount());
        assertEquals(1, desk.preferentialWaitingCount());

        assertEquals(Operation.Type.QUEUED, desk.undo(T0).getType());
        assertEquals(0, desk.preferentialWaitingCount());

        // Cancelar a reserva liberta o lugar, e o primeiro da fila entra logo.
        assertTrue(desk.cancelReservation("RES001MC", T0.plusMinutes(1)));
        assertEquals(0, desk.normalWaitingCount());
        assertNotNull(desk.findForExit("WAIT01MC"));
        assertFalse(desk.canUndo());
        assertFalse(desk.admitWaiting(T0.plusMinutes(2)).iterator().hasNext());
    }

    // ------------------------------------------------------------------
    // Bloqueio da conta (RF03)
    // ------------------------------------------------------------------

    @Test
    void aLockedAccountStaysLockedAfterReopeningUntilAnAdminUnlocksIt() throws SQLException {
        ParkingDesk desk = adminDesk();
        desk.registerUser("ana", "operadora-1".toCharArray(), User.Role.OPERATOR);
        for (int i = 0; i < AuthService.MAX_FAILED_ATTEMPTS; i++) {
            assertNull(desk.login("ana", "errada-123".toCharArray(), T0));
        }
        assertEquals(T0.plusMinutes(AuthService.LOCK_MINUTES), desk.lockedUntil("ana", T0));
        assertNull(desk.login("ana", "operadora-1".toCharArray(), T0.plusMinutes(1)));

        ParkingDesk reopened = open();
        assertEquals(T0.plusMinutes(AuthService.LOCK_MINUTES), reopened.lockedUntil("ana", T0.plusMinutes(2)));
        assertNull(reopened.login("ana", "operadora-1".toCharArray(), T0.plusMinutes(2)));

        reopened.login("admin", ADMIN_PASSWORD, T0.plusMinutes(3));
        assertTrue(reopened.unlockUser("ana"));
        assertNull(reopened.lockedUntil("ana", T0.plusMinutes(3)));
        assertNotNull(reopened.login("ana", "operadora-1".toCharArray(), T0.plusMinutes(4)));

        reopened.login("admin", ADMIN_PASSWORD, T0.plusMinutes(5));
        assertEquals(1, count(reopened.auditLog(T0, T0.plusDays(1), null, AuditEntry.Action.ACCOUNT_LOCKED)));
        assertEquals(AuthService.MAX_FAILED_ATTEMPTS + 2,
                count(reopened.auditLog(T0, T0.plusDays(1), "ana", AuditEntry.Action.LOGIN_FAILED)));
        // A gestão de utilizadores regista a hora do relógio, por isso o período é largo.
        assertEquals(1, count(reopened.auditLog(T0.minusYears(1), T0.plusYears(1), "admin",
                AuditEntry.Action.USER_UNLOCKED)));
    }

    // ------------------------------------------------------------------
    // Avenças, talão perdido, pesquisa e histórico
    // ------------------------------------------------------------------

    @Test
    void aPassCoversTheStayAndALostTicketPaysTheFine() throws SQLException {
        ParkingDesk desk = adminDesk();
        Tariff tariff = desk.getTariff();
        MonthlyPass pass = desk.sellPass("Maria", null, "pas-001-mc", YearMonth.from(T0), Payment.Method.CASH, T0);
        assertEquals(tariff.getMonthlyPassCents(), pass.getAmountCents());
        assertThrows(IllegalStateException.class,
                () -> desk.sellPass("Maria", null, "PAS001MC", YearMonth.from(T0), Payment.Method.CASH, T0));

        LocalDateTime at = T0.plusHours(2);
        ParkingSession covered = desk.enter("PAS001MC", Vehicle.Type.CAR, 60, T0);
        assertEquals(pass, desk.validPass("PAS001MC", at));
        assertEquals(0, desk.quote(covered, at));
        assertEquals(tariff.getLostTicketCents(), desk.quote(covered, at, true));
        Payment free = desk.exit(covered, Payment.Method.CASH, at).getPayment();
        assertEquals(0, free.getAmountCents());
        assertEquals(Payment.Method.PASS, free.getMethod());

        ParkingSession lost = desk.enter("AAA001MC", Vehicle.Type.CAR, 60, T0);
        long stay = desk.quote(lost, at);
        Payment fined = desk.exit(lost, Payment.Method.MPESA, at, true).getPayment();
        assertEquals(stay + tariff.getLostTicketCents(), fined.getAmountCents());
        assertEquals(tariff.getLostTicketCents(), fined.getLostTicketCents());

        RevenueReport report = desk.revenue(T0, T0.plusDays(1));
        assertEquals(3, report.getCount());
        assertEquals(1, report.getPassCount());
        assertEquals(pass.getAmountCents() + fined.getAmountCents(), report.getTotalCents());
        assertEquals(3, desk.cashClose(T0, T0.plusDays(1)).getReport().getCount());
        assertEquals(1, count(desk.auditLog(T0, T0.plusDays(1), null, AuditEntry.Action.LOST_TICKET)));
        assertEquals(1, count(desk.auditLog(T0, T0.plusDays(1), null, AuditEntry.Action.PASS_SOLD)));

        // Depois de reabrir, a avença e a multa vêm da base.
        ParkingDesk reopened = open();
        reopened.login("admin", ADMIN_PASSWORD, T0);
        assertNotNull(reopened.validPass("PAS001MC", at));
        assertEquals(1, count(reopened.passes()));
        assertEquals(report.getTotalCents(), reopened.revenue(T0, T0.plusDays(1)).getTotalCents());
        assertEquals(1, TestDatabase.count(db, "SELECT COUNT(*) FROM payments WHERE lost_ticket_cents > 0"));
    }

    @Test
    void searchFindsParkedCarsWithHowDeepTheyAre() throws SQLException {
        ParkingDesk desk = adminDesk();
        desk.enter("AAA001MC", Vehicle.Type.CAR, 60, T0);
        desk.enter("AAA002MC", Vehicle.Type.CAR, 60, T0);
        desk.enter("BBB003MC", Vehicle.Type.CAR, 60, T0);

        StringBuilder found = new StringBuilder();
        for (ParkedCar car : desk.parkedCars("aaa 00")) {
            found.append(car.getSession().getVehicle().getPlate()).append('/').append(car.getDepth())
                    .append('/').append(car.getManoeuvres()).append(' ');
        }
        assertEquals("AAA002MC/1/2 AAA001MC/2/4 ", found.toString());
        assertEquals(3, count(desk.parkedCars("")));
        assertEquals(1, count(desk.parkedCars("3")));
        assertEquals(0, count(desk.parkedCars("ZZZ")));
    }

    @Test
    void historyAndAuditAreOnlyForAdmins() throws SQLException {
        ParkingDesk desk = adminDesk();
        desk.registerUser("ana", "operadora-1".toCharArray(), User.Role.OPERATOR);
        desk.enter("AAA001MC", Vehicle.Type.CAR, 60, T0);
        desk.enter("AAA002MC", Vehicle.Type.CAR, 60, T0.plusMinutes(5));
        desk.exit(desk.findForExit("AAA001MC"), Payment.Method.CASH, T0.plusHours(1));

        LocalDateTime dayEnd = T0.plusDays(1);
        assertEquals(2, count(desk.history(T0, dayEnd, "", null, true, true)));
        assertEquals(1, count(desk.history(T0, dayEnd, "", null, false, true)));
        assertEquals(1, count(desk.history(T0, dayEnd, "", "admin", true, true)));
        assertEquals(1, count(desk.auditLog(T0.minusYears(1), T0.plusYears(1), null,
                AuditEntry.Action.USER_CREATED)));

        desk.login("ana", "operadora-1".toCharArray(), T0);
        assertThrows(SecurityException.class, () -> desk.history(T0, dayEnd, "", null, true, true));
        assertThrows(SecurityException.class, () -> desk.auditLog(T0, dayEnd, null, null));
    }

    // ------------------------------------------------------------------
    // Cópia de segurança (RF24)
    // ------------------------------------------------------------------

    @Test
    void restoringABackupBringsBackExactlyWhatWasSaved(@TempDir Path folder) throws SQLException, IOException {
        ParkingDesk desk = adminDesk();
        desk.registerUser("ana", "operadora-1".toCharArray(), User.Role.OPERATOR);
        desk.enter("AAA001MC", Vehicle.Type.CAR, 60, T0);
        desk.sellPass("Maria", "84 000 0000", "PAS001MC", YearMonth.from(T0), Payment.Method.CASH, T0);
        Path file = folder.resolve("parque.backup");
        assertTrue(desk.backup(file, T0) > 0);

        desk.enter("AAA002MC", Vehicle.Type.CAR, 60, T0.plusMinutes(1));
        desk.removeUser("ana");
        assertEquals(desk.totalPlaces() - 2, desk.freeForWalkIns(T0.plusMinutes(1)));

        desk.restore(file, T0.plusMinutes(2));
        assertNull(desk.getCurrentUser());

        ParkingDesk reopened = open();
        reopened.login("admin", ADMIN_PASSWORD, T0.plusMinutes(3));
        assertEquals("AAA001MC", plates(lane(reopened, "C01")));
        assertNotNull(reopened.login("ana", "operadora-1".toCharArray(), T0.plusMinutes(4)));
        reopened.login("admin", ADMIN_PASSWORD, T0.plusMinutes(5));
        assertEquals(1, count(reopened.passes()));
        assertEquals(2, reopened.enter("AAA003MC", Vehicle.Type.CAR, 60, T0).getTicketNumber());
        assertEquals(1, count(reopened.auditLog(T0, T0.plusDays(1), "admin", AuditEntry.Action.BACKUP_RESTORED)));
    }

    @Test
    void aFileThatIsNotABackupChangesNothing(@TempDir Path folder) throws SQLException, IOException {
        ParkingDesk desk = adminDesk();
        desk.enter("AAA001MC", Vehicle.Type.CAR, 60, T0);
        Path file = folder.resolve("outro.txt");
        Files.write(file, "isto não é uma cópia".getBytes(StandardCharsets.UTF_8));

        assertThrows(IOException.class, () -> desk.restore(file, T0));
        assertNotNull(desk.getCurrentUser());
        assertEquals(1, TestDatabase.count(db, "SELECT COUNT(*) FROM parking_sessions"));

        desk.login("ana", "x".toCharArray(), T0);
        assertThrows(SecurityException.class, () -> desk.backup(folder.resolve("b"), T0));
    }

    // ------------------------------------------------------------------

    private static int count(Iterable<?> items) {
        int n = 0;
        for (Object item : items) {
            n++;
        }
        return n;
    }

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
