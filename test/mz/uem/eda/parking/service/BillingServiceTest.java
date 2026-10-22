package mz.uem.eda.parking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import mz.uem.eda.parking.model.ParkingSession;
import mz.uem.eda.parking.model.Payment;
import mz.uem.eda.parking.model.StayClass;
import mz.uem.eda.parking.model.Tariff;
import mz.uem.eda.parking.model.User;
import mz.uem.eda.parking.model.Vehicle;

class BillingServiceTest {

    private static final LocalDateTime ENTRY = LocalDateTime.of(2026, 10, 20, 8, 0);

    /** Tolerância 15 min, fracção 15 min, 60 MZN por hora, tecto 400 MZN. */
    private BillingService billing;

    @BeforeEach
    void setUp() {
        billing = new BillingService(new Tariff(15, 15, 6000, 40000));
    }

    // ---- tolerância -----------------------------------------------------

    @Test
    void freeWithinTheTolerance() {
        assertEquals(0, billing.amountFor(0));
        assertEquals(0, billing.amountFor(10));
        assertEquals(0, billing.amountFor(15));
    }

    @Test
    void afterTheTolerancePaysTheWholeStay() {
        // 16 min: a tolerância não se desconta, e 16 min são 2 fracções de 15
        assertEquals(3000, billing.amountFor(16));
    }

    // ---- fracção mínima -------------------------------------------------

    @Test
    void roundsUpToTheFraction() {
        assertEquals(6000, billing.amountFor(60));    // 4 fracções
        assertEquals(7500, billing.amountFor(61));    // 5 fracções
        assertEquals(7500, billing.amountFor(75));
    }

    @Test
    void roundsUpToTheCentWhenTheFractionIsNotWhole() {
        // 50 MZN por hora em fracções de 20 min: 16,666... MZN por fracção
        BillingService odd = new BillingService(new Tariff(0, 20, 5000, 40000));
        assertEquals(1667, odd.amountFor(20));
        assertEquals(3334, odd.amountFor(40));
        assertEquals(5000, odd.amountFor(60));        // sem erro acumulado
    }

    // ---- tecto diário ---------------------------------------------------

    @Test
    void neverPassesTheDailyCap() {
        assertEquals(36000, billing.amountFor(6 * 60));
        assertEquals(40000, billing.amountFor(10 * 60));
        assertEquals(40000, billing.amountFor(24 * 60));
    }

    @Test
    void eachFullDayPaysTheCap() {
        assertEquals(40000 + 6000, billing.amountFor(25 * 60));
        assertEquals(2 * 40000 + 40000, billing.amountFor(2 * 24 * 60 + 10 * 60));
        assertEquals(3 * 40000, billing.amountFor(3 * 24 * 60));
    }

    // ---- minutos --------------------------------------------------------

    @Test
    void aStartedMinuteCountsWhole() {
        assertEquals(15, BillingService.minutesBetween(ENTRY, ENTRY.plusMinutes(15)));
        assertEquals(16, BillingService.minutesBetween(ENTRY, ENTRY.plusMinutes(15).plusSeconds(1)));
        assertEquals(0, BillingService.minutesBetween(ENTRY, ENTRY));
    }

    @Test
    void amountDueUsesTheEntryTime() {
        ParkingSession session = session();
        assertEquals(0, billing.amountDue(session, ENTRY.plusMinutes(15)));
        assertEquals(3000, billing.amountDue(session, ENTRY.plusMinutes(15).plusSeconds(1)));
    }

    @Test
    void rejectsTimesBeforeTheEntry() {
        assertThrows(IllegalArgumentException.class,
                () -> billing.amountDue(session(), ENTRY.minusMinutes(1)));
        assertThrows(IllegalArgumentException.class, () -> billing.amountFor(-1));
    }

    // ---- pagamento ------------------------------------------------------

    @Test
    void payRecordsTheAmountDue() {
        ParkingSession session = session();
        User operator = new User("op1", "hash", "salt", 1, User.Role.OPERATOR);
        LocalDateTime at = ENTRY.plusHours(2);

        Payment payment = billing.pay(session, Payment.Method.MPESA, at, operator);

        assertEquals(12000, payment.getAmountCents());
        assertSame(session, payment.getSession());
        assertEquals(Payment.Method.MPESA, payment.getMethod());
        assertEquals(at, payment.getPaidAt());
    }

    @Test
    void cannotPayAClosedSession() {
        ParkingSession session = session();
        session.close(ENTRY.plusHours(1), 0);
        User operator = new User("op1", "hash", "salt", 1, User.Role.OPERATOR);

        assertThrows(IllegalStateException.class,
                () -> billing.pay(session, Payment.Method.CASH, ENTRY.plusHours(1), operator));
    }

    @Test
    void newTariffAppliesFromThenOn() {
        billing.setTariff(new Tariff(0, 60, 10000, 50000));
        assertEquals(10000, billing.amountFor(1));
        assertThrows(NullPointerException.class, () -> billing.setTariff(null));
    }

    // ---- auxiliares -----------------------------------------------------

    private static ParkingSession session() {
        Vehicle vehicle = new Vehicle("AAA001MC", Vehicle.Type.CAR, null);
        return new ParkingSession(1, vehicle, "L01", StayClass.SHORT, 60, ENTRY);
    }
}
