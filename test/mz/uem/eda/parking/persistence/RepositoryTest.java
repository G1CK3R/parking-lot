package mz.uem.eda.parking.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Properties;

import org.junit.jupiter.api.Test;

import mz.uem.eda.parking.model.Lane;
import mz.uem.eda.parking.model.ParkingSession;
import mz.uem.eda.parking.model.Reservation;
import mz.uem.eda.parking.model.Tariff;
import mz.uem.eda.parking.model.User;
import mz.uem.eda.parking.model.Vehicle;
import mz.uem.eda.parking.persistence.security.PasswordHasher;
import mz.uem.eda.parking.service.access.AuthService;
import mz.uem.eda.parking.service.parking.EntryService;
import mz.uem.eda.parking.service.parking.ReservationService;
import mz.uem.eda.parking.service.policy.FirstWithSpace;
import mz.uem.eda.parking.structures.Queue;

/**
 * Utilizadores, tarifas, reservas e a configuração da ligação. Os testes com
 * base de dados saltam se não houver MySQL configurado.
 */
class RepositoryTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 10, 21, 8, 0);
    private final PasswordHasher hasher = new PasswordHasher(1000);

    // ---- ConnectionFactory (sem base de dados) --------------------------

    @Test
    void configNeedsTheThreeKeys() {
        Properties p = new Properties();
        p.setProperty("db.url", "jdbc:mysql://localhost:3306/parking_lot");
        p.setProperty("db.user", "parking");
        assertThrows(IllegalArgumentException.class, () -> ConnectionFactory.fromProperties(p));

        p.setProperty("db.password", "segredo");
        ConnectionFactory f = ConnectionFactory.fromProperties(p);
        assertEquals("jdbc:mysql://localhost:3306/parking_lot", f.getUrl());
        assertFalse(f.toString().contains("segredo"));
    }

    // ---- utilizadores ---------------------------------------------------

    @Test
    void usersSurviveAReopenAndStillLogIn() throws SQLException {
        ConnectionFactory db = TestDatabase.fresh();
        UserRepository repo = new UserRepository(db);
        AuthService auth = new AuthService(hasher);
        User admin = auth.createFirstAdmin("admin", "admin-pass".toCharArray());
        repo.insert(admin);
        User ana = auth.register(admin, "ana", "ana-pass1".toCharArray(), User.Role.OPERATOR);
        repo.insert(ana);
        auth.changePassword(ana, "ana-pass1".toCharArray(), "ana-pass2".toCharArray());
        repo.updateCredentials(ana);

        AuthService reopened = new AuthService(hasher);
        for (User user : repo.loadAll()) {
            reopened.load(user);
        }
        assertEquals(2, reopened.size());
        assertNull(reopened.login("ana", "ana-pass1".toCharArray()));
        User back = reopened.login("ana", "ana-pass2".toCharArray());
        assertEquals(ana.getId(), back.getId());
        assertEquals(User.Role.OPERATOR, back.getRole());
    }

    @Test
    void removedUsersAreDeactivatedNotDeleted() throws SQLException {
        ConnectionFactory db = TestDatabase.fresh();
        UserRepository repo = new UserRepository(db);
        AuthService auth = new AuthService(hasher);
        User admin = auth.createFirstAdmin("admin", "admin-pass".toCharArray());
        repo.insert(admin);
        User rui = auth.register(admin, "rui", "rui-pass1".toCharArray(), User.Role.OPERATOR);
        repo.insert(rui);

        auth.remove(admin, "rui");
        repo.deactivate(rui);

        assertEquals(1, repo.loadAll().size());
        assertEquals(2, TestDatabase.count(db, "SELECT COUNT(*) FROM users"));
        assertThrows(SQLException.class, () -> repo.insert(
                new User("admin", "h", "s", 1, User.Role.OPERATOR)));        // nome repetido
        assertThrows(IllegalStateException.class, () -> repo.insert(admin)); // já gravado
    }

    // ---- tarifas --------------------------------------------------------

    @Test
    void seedTariffAndReplacingIt() throws SQLException {
        ConnectionFactory db = TestDatabase.fresh();
        TariffRepository repo = new TariffRepository(db);

        Tariff seed = repo.loadActive();
        assertEquals(15, seed.getFreeMinutes());
        assertEquals(6000, seed.getHourlyRateCents());

        Tariff newer = new Tariff(10, 30, 8000, 50000);
        repo.replaceActive(newer, T0);
        Tariff loaded = repo.loadActive();

        assertNotEquals(0, newer.getId());
        assertEquals(newer.getId(), loaded.getId());
        assertEquals(8000, loaded.getHourlyRateCents());
        assertEquals(1, TestDatabase.count(db, "SELECT COUNT(*) FROM tariffs WHERE active = TRUE"));
        assertEquals(2, TestDatabase.count(db, "SELECT COUNT(*) FROM tariffs"));

        // todas, a antiga à frente (ADR-031)
        Queue<Tariff> all = repo.loadAll();
        assertEquals(2, all.size());
        assertEquals(seed.getId(), all.dequeue().getId());
        assertEquals(newer.getId(), all.dequeue().getId());
    }

    // ---- reservas -------------------------------------------------------

    @Test
    void activeReservationsComeBackInOrderAndStaleOnesExpire() throws SQLException {
        ConnectionFactory db = TestDatabase.fresh();
        ReservationRepository repo = new ReservationRepository(db);
        Queue<Lane> lanes = new LaneRepository(db).loadAll();
        ReservationService service = new ReservationService(lanes, new EntryService(lanes, new FirstWithSpace()), 30);

        repo.insert(service.reserve("AAA001MC", 60, T0));
        repo.insert(service.reserve("AAA002MC", 60, T0.plusMinutes(10)));
        repo.insert(service.reserve("AAA003MC", 60, T0.plusMinutes(20)));

        // reabre às 8h35: a das 8h00 já expirou
        Queue<Reservation> active = repo.loadActive(T0.plusMinutes(35));
        assertEquals(2, active.size());
        assertEquals("AAA002MC", active.peek().getPlate());
        assertEquals(1, TestDatabase.count(db, "SELECT COUNT(*) FROM reservations WHERE status = 'EXPIRED'"));

        ReservationService reopened = new ReservationService(lanes, new EntryService(lanes, new FirstWithSpace()), 30);
        for (Reservation r : active) {
            reopened.load(r);
        }
        assertEquals(2, reopened.activeCount());
        assertEquals(198, reopened.availableForWalkIns(T0.plusMinutes(35)));
    }

    @Test
    void checkInIsSavedWithTheEntry() throws SQLException {
        ConnectionFactory db = TestDatabase.fresh();
        LaneRepository laneRepo = new LaneRepository(db);
        SessionRepository sessions = new SessionRepository(db, laneRepo);
        ReservationRepository repo = new ReservationRepository(db);
        Queue<Lane> lanes = laneRepo.loadAll();
        EntryService entry = new EntryService(lanes, new FirstWithSpace());
        ReservationService service = new ReservationService(lanes, entry, 30);

        Reservation r = service.reserve("AAA001MC", 60, T0);
        repo.insert(r);
        ParkingSession s = service.checkIn(new Vehicle("AAA001MC", Vehicle.Type.CAR), T0.plusMinutes(5));
        Lane lane = null;
        for (Lane l : lanes) {
            if (l.getCode().equals(s.getLaneCode())) {
                lane = l;
            }
        }
        sessions.recordEntry(s, lane, entry.getNextTicketNumber(), r);

        assertEquals(1, TestDatabase.count(db, "SELECT COUNT(*) FROM reservations WHERE status = 'CHECKED_IN'"));
        assertEquals(0, repo.loadActive(T0.plusMinutes(6)).size());
        assertSame(Reservation.Status.CHECKED_IN, r.getStatus());
        assertTrue(s.getId() > 0);
    }
}
