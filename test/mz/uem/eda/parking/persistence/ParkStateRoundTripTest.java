package mz.uem.eda.parking.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import mz.uem.eda.parking.model.Customer;
import mz.uem.eda.parking.model.Lane;
import mz.uem.eda.parking.model.ParkingSession;
import mz.uem.eda.parking.model.Payment;
import mz.uem.eda.parking.model.RetrievalPlan;
import mz.uem.eda.parking.model.StayClass;
import mz.uem.eda.parking.model.Tariff;
import mz.uem.eda.parking.model.User;
import mz.uem.eda.parking.model.Vehicle;
import mz.uem.eda.parking.persistence.security.PasswordHasher;
import mz.uem.eda.parking.service.AuthService;
import mz.uem.eda.parking.service.BillingService;
import mz.uem.eda.parking.service.EntryService;
import mz.uem.eda.parking.service.ExitService;
import mz.uem.eda.parking.service.PaymentHistoryService;
import mz.uem.eda.parking.service.policy.FirstWithSpace;
import mz.uem.eda.parking.structures.Queue;

/**
 * O ciclo completo contra uma base real: entradas e saídas gravadas, e o
 * parque reaberto exactamente como ficou.
 */
class ParkStateRoundTripTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 10, 21, 8, 0);

    private ConnectionFactory db;
    private LaneRepository laneRepo;
    private SessionRepository sessionRepo;
    private User operator;

    @BeforeEach
    void setUp() throws SQLException {
        db = TestDatabase.fresh();
        laneRepo = new LaneRepository(db);
        sessionRepo = new SessionRepository(db, laneRepo);
        AuthService auth = new AuthService(new PasswordHasher(1000));
        operator = auth.createFirstAdmin("admin", "admin-pass".toCharArray());
        new UserRepository(db).insert(operator);
    }

    @Test
    void seedHas22LanesWith200PlacesAndTheBuffer() throws SQLException {
        Queue<Lane> lanes = laneRepo.loadAll();

        int places = 0;
        int buffers = 0;
        for (Lane lane : lanes) {
            if (lane.isBuffer()) {
                buffers++;
            } else {
                places += lane.getCapacity();
            }
            assertTrue(lane.isEmpty());
            assertNotEquals(0, lane.getId());
        }
        assertEquals(23, lanes.size());
        assertEquals(1, buffers);
        assertEquals(200, places);
        assertEquals("C01", lanes.peek().getCode());
        assertEquals(1, sessionRepo.loadNextTicketNumber());
    }

    @Test
    void reopensWithTheSameStacksAndTicketNumbers() throws SQLException {
        Queue<Lane> lanes = laneRepo.loadAll();
        EntryService entry = new EntryService(lanes, new FirstWithSpace());
        Customer ana = new Customer("Ana", "840000000", true);

        enter(entry, lanes, new Vehicle("AAA 001 MC", Vehicle.Type.CAR, ana));
        enter(entry, lanes, new Vehicle("AAA002MC", Vehicle.Type.VAN, null));
        enter(entry, lanes, new Vehicle("AAA003MC", Vehicle.Type.MOTORCYCLE, null));

        Queue<Lane> reopened = laneRepo.loadAll();
        Lane c01 = find(reopened, "C01");
        assertEquals("AAA003MC AAA002MC AAA001MC", plates(c01));     // topo -> fundo
        ParkingSession bottom = sessionAt(c01, 2);
        assertEquals(1, bottom.getTicketNumber());
        assertEquals(T0, bottom.getEntryTime());
        assertEquals(StayClass.SHORT, bottom.getStayClass());
        assertTrue(bottom.getVehicle().getCustomer().isPreferential());
        assertEquals(4, sessionRepo.loadNextTicketNumber());

        EntryService restored = new EntryService(reopened, new FirstWithSpace());
        restored.restoreNextTicketNumber(sessionRepo.loadNextTicketNumber());
        assertEquals(4, restored.enter(new Vehicle("BBB001MC", Vehicle.Type.CAR, null), 60, T0).getTicketNumber());
        assertTrue(restored.isParked(new Vehicle("AAA001MC", Vehicle.Type.CAR, null)));
    }

    @Test
    void exitAndPaymentAreSavedTogether() throws SQLException {
        Queue<Lane> lanes = laneRepo.loadAll();
        EntryService entry = new EntryService(lanes, new FirstWithSpace());
        ExitService exit = new ExitService(lanes);
        BillingService billing = new BillingService(new TariffRepository(db).loadActive());
        for (int i = 1; i <= 3; i++) {
            enter(entry, lanes, new Vehicle("AAA00" + i + "MC", Vehicle.Type.CAR, null));
        }

        LocalDateTime out = T0.plusHours(2);
        ParkingSession session = exit.findByPlate("AAA001MC");
        Payment payment = billing.pay(session, Payment.Method.MPESA, out, operator);
        RetrievalPlan plan = exit.exitByTicket(session.getTicketNumber(), out);
        sessionRepo.recordExit(session, find(lanes, "C01"), payment);

        assertNotEquals(0, payment.getId());
        assertEquals(1, TestDatabase.count(db, "SELECT COUNT(*) FROM payments WHERE amount_cents = 12000"));
        assertEquals(1, TestDatabase.count(db,
                "SELECT COUNT(*) FROM parking_sessions WHERE ticket_no = 1 AND status = 'CLOSED' AND moves_count = 4"));
        assertEquals(4, plan.getManoeuvreCount());

        Lane c01 = find(laneRepo.loadAll(), "C01");
        assertEquals("AAA003MC AAA002MC", plates(c01));
    }

    @Test
    void failedExitLeavesNothingSaved() throws SQLException {
        Queue<Lane> lanes = laneRepo.loadAll();
        EntryService entry = new EntryService(lanes, new FirstWithSpace());
        enter(entry, lanes, new Vehicle("AAA001MC", Vehicle.Type.CAR, null));

        // sessão que nunca foi gravada: o INSERT do pagamento não encontra o talão
        Vehicle ghost = new Vehicle("ZZZ999MC", Vehicle.Type.CAR, null);
        ParkingSession unsaved = new ParkingSession(99, ghost, "C01", StayClass.SHORT, 60, T0);
        Tariff tariff = new TariffRepository(db).loadActive();
        Payment payment = new Payment(unsaved, tariff, 500, Payment.Method.CASH, T0.plusHours(1), operator);
        unsaved.close(T0.plusHours(1), 0);
        Lane emptied = Lane.parking("C01", 5, StayClass.SHORT);

        assertThrows(SQLException.class, () -> sessionRepo.recordExit(unsaved, emptied, payment));
        assertEquals(0, TestDatabase.count(db, "SELECT COUNT(*) FROM payments"));
        assertEquals("AAA001MC", plates(find(laneRepo.loadAll(), "C01")));
    }

    @Test
    void exitRequiresAClosedSessionASavedOperatorAndASavedTariff() throws SQLException {
        Queue<Lane> lanes = laneRepo.loadAll();
        EntryService entry = new EntryService(lanes, new FirstWithSpace());
        ParkingSession session = enter(entry, lanes, new Vehicle("AAA001MC", Vehicle.Type.CAR, null));
        User unsaved = new User("ghost", "hash", "salt", 1, User.Role.OPERATOR);
        Tariff tariff = new TariffRepository(db).loadActive();
        Payment payment = new Payment(session, tariff, 0, Payment.Method.CASH, T0, operator);
        Payment byGhost = new Payment(session, tariff, 0, Payment.Method.CASH, T0, unsaved);
        Payment unsavedTariff = new Payment(session, new Tariff(15, 15, 6000, 40000), 0,
                Payment.Method.CASH, T0, operator);
        Lane c01 = find(lanes, "C01");

        assertThrows(IllegalArgumentException.class, () -> sessionRepo.recordExit(session, c01, payment));
        session.close(T0.plusMinutes(5), 0);
        assertThrows(IllegalArgumentException.class, () -> sessionRepo.recordExit(session, c01, byGhost));
        assertThrows(IllegalArgumentException.class, () -> sessionRepo.recordExit(session, c01, unsavedTariff));
    }

    @Test
    void paymentHistoryComesBackWithTheTariffOfTheTime() throws SQLException {
        Queue<Lane> lanes = laneRepo.loadAll();
        EntryService entry = new EntryService(lanes, new FirstWithSpace());
        ExitService exit = new ExitService(lanes);
        TariffRepository tariffRepo = new TariffRepository(db);
        BillingService billing = new BillingService(tariffRepo.loadActive());
        enter(entry, lanes, new Vehicle("AAA001MC", Vehicle.Type.CAR, null));
        enter(entry, lanes, new Vehicle("AAA002MC", Vehicle.Type.CAR, null));

        // o carro do fundo sai com a tarifa do seed: 2 h a 60 MZN, 2 manobras
        leave(exit, billing, lanes, "AAA001MC", T0.plusHours(2));
        // mudam os preços; o outro carro sai com a tarifa nova: 4 h a 80 MZN
        Tariff newer = new Tariff(10, 30, 8000, 50000);
        tariffRepo.replaceActive(newer, T0.plusHours(3));
        billing.setTariff(newer);
        leave(exit, billing, lanes, "AAA002MC", T0.plusHours(4));

        // reabrir: tarifas primeiro, depois os pagamentos ligados a elas
        Queue<Tariff> tariffs = tariffRepo.loadAll();
        PaymentHistoryService history = new PaymentHistoryService();
        for (Payment p : sessionRepo.loadPayments(tariffs)) {
            history.add(p);
        }

        assertEquals(2, tariffs.size());
        assertEquals(2, history.size());
        Payment old = history.findByTicket(1);
        assertEquals(12000, old.getAmountCents());
        assertEquals(6000, old.getTariff().getHourlyRateCents());     // a tarifa já substituída
        assertSame(tariffs.peek(), old.getTariff());                 // o mesmo objecto da fila
        assertEquals(Payment.Method.CASH, old.getMethod());
        assertEquals("admin", old.getOperator().getUsername());
        assertEquals("AAA001MC", old.getSession().getVehicle().getPlate());
        assertEquals(T0.plusHours(2), old.getSession().getExitTime());
        assertEquals(2, old.getSession().getManoeuvres());
        Payment recent = history.findByTicket(2);
        assertEquals(32000, recent.getAmountCents());
        assertEquals(newer.getId(), recent.getTariff().getId());
    }

    @Test
    void sameVehicleIsReusedOnTheNextVisit() throws SQLException {
        Queue<Lane> lanes = laneRepo.loadAll();
        EntryService entry = new EntryService(lanes, new FirstWithSpace());
        ExitService exit = new ExitService(lanes);
        BillingService billing = new BillingService(new TariffRepository(db).loadActive());

        ParkingSession first = enter(entry, lanes, new Vehicle("AAA001MC", Vehicle.Type.CAR, null));
        Payment p = billing.pay(first, Payment.Method.CASH, T0.plusMinutes(10), operator);
        exit.exitByTicket(first.getTicketNumber(), T0.plusMinutes(10));
        sessionRepo.recordExit(first, find(lanes, "C01"), p);
        enter(entry, lanes, new Vehicle("aaa-001-mc", Vehicle.Type.CAR, null));

        assertEquals(1, TestDatabase.count(db, "SELECT COUNT(*) FROM vehicles"));
        assertEquals(2, TestDatabase.count(db, "SELECT COUNT(*) FROM parking_sessions"));
        assertEquals(0, p.getAmountCents());                         // dentro da tolerância
    }

    // ---- auxiliares -----------------------------------------------------

    /** Paga, tira o carro e grava a saída, como faz a interface (RF08). */
    private void leave(ExitService exit, BillingService billing, Queue<Lane> lanes, String plate,
            LocalDateTime at) throws SQLException {
        ParkingSession session = exit.findByPlate(plate);
        Payment payment = billing.pay(session, Payment.Method.CASH, at, operator);
        exit.exitByTicket(session.getTicketNumber(), at);
        sessionRepo.recordExit(session, find(lanes, session.getLaneCode()), payment);
    }

    private ParkingSession enter(EntryService entry, Queue<Lane> lanes, Vehicle vehicle) throws SQLException {
        ParkingSession session = entry.enter(vehicle, 60, T0);
        sessionRepo.recordEntry(session, find(lanes, session.getLaneCode()), entry.getNextTicketNumber(), null);
        assertNotEquals(0, session.getId());
        assertNotEquals(0, vehicle.getId());
        return session;
    }

    private static Lane find(Queue<Lane> lanes, String code) {
        for (Lane lane : lanes) {
            if (lane.getCode().equals(code)) {
                return lane;
            }
        }
        throw new AssertionError("baia em falta: " + code);
    }

    private static String plates(Lane lane) {
        StringBuilder text = new StringBuilder();
        for (ParkingSession s : lane.contents()) {
            if (text.length() > 0) {
                text.append(' ');
            }
            text.append(s.getVehicle().getPlate());
        }
        return text.toString();
    }

    private static ParkingSession sessionAt(Lane lane, int depth) {
        int i = 0;
        for (ParkingSession s : lane.contents()) {
            if (i++ == depth) {
                return s;
            }
        }
        throw new AssertionError("profundidade fora da baia: " + depth);
    }
}
