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
        assertSame(billing.getTariff(), payment.getTariff());       // ADR-031
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

    // ---- noite, fim-de-semana, multa e avença (RF16, RF17) ----------------

    /**
     * Tolerância 15 min, fracção 15 min, 60 MZN por hora de dia e 30 de
     * noite, tecto 400 MZN, 20 % de desconto ao fim-de-semana, multa de 500
     * MZN e avença de 3500 MZN. O dia 20/10/2026 é uma terça e o 24 um sábado.
     */
    private static final Tariff PERIODS = new Tariff(15, 15, 6000, 40000, 3000, 20, 50000, 350000);

    @Test
    void theNightPaysTheNightRate() {
        BillingService night = new BillingService(PERIODS);
        LocalDateTime tuesday21h = LocalDateTime.of(2026, 10, 20, 21, 0);

        assertEquals(6000, night.amountBetween(tuesday21h, tuesday21h.plusHours(2)));
    }

    @Test
    void eachFractionPaysTheRateOfTheHourItStarts() {
        BillingService night = new BillingService(PERIODS);
        LocalDateTime tuesday19h = LocalDateTime.of(2026, 10, 20, 19, 0);

        // 1 h de dia (60 MZN) e 1 h de noite (30 MZN).
        assertEquals(9000, night.amountBetween(tuesday19h, tuesday19h.plusHours(2)));
        // Às 7h00 já é dia.
        LocalDateTime tuesday6h = LocalDateTime.of(2026, 10, 20, 6, 0);
        assertEquals(3000 + 6000, night.amountBetween(tuesday6h, tuesday6h.plusHours(2)));
    }

    @Test
    void theWeekendHasTheDiscount() {
        BillingService weekend = new BillingService(PERIODS);
        LocalDateTime saturday10h = LocalDateTime.of(2026, 10, 24, 10, 0);

        // 3 h a 60 MZN com 20 % de desconto: 144 MZN em vez de 180.
        assertEquals(14400, weekend.amountBetween(saturday10h, saturday10h.plusHours(3)));
    }

    @Test
    void fullDaysStillPayTheCapAndTheRestStartsADayLater() {
        BillingService periods = new BillingService(PERIODS);
        LocalDateTime tuesday10h = LocalDateTime.of(2026, 10, 20, 10, 0);

        assertEquals(40000 + 12000, periods.amountBetween(tuesday10h, tuesday10h.plusHours(26)));
        assertEquals(40000, periods.amountBetween(tuesday10h, tuesday10h.plusHours(10)));
        // Sem saber a hora, conta tudo ao valor de dia.
        assertEquals(12000, periods.amountFor(120));
    }

    @Test
    void withoutPeriodsTheResultIsTheSameAsBefore() {
        LocalDateTime saturday21h = LocalDateTime.of(2026, 10, 24, 21, 0);
        for (int minutes = 0; minutes <= 3000; minutes += 7) {
            assertEquals(billing.amountFor(minutes),
                    billing.amountBetween(saturday21h, saturday21h.plusMinutes(minutes)));
        }
    }

    @Test
    void aLostTicketAddsTheFineAndAPassCoversTheStay() {
        BillingService periods = new BillingService(PERIODS);
        ParkingSession session = session();
        LocalDateTime at = ENTRY.plusHours(2);

        assertEquals(12000, periods.quote(session, at, false, false));
        assertEquals(12000 + 50000, periods.quote(session, at, true, false));
        assertEquals(0, periods.quote(session, at, false, true));
        assertEquals(50000, periods.quote(session, at, true, true));
    }

    @Test
    void aPaymentCoveredByThePassIsRecordedAsPass() {
        BillingService periods = new BillingService(PERIODS);
        User operator = new User("op1", "hash", "salt", 1, User.Role.OPERATOR);
        LocalDateTime at = ENTRY.plusHours(2);

        Payment covered = periods.pay(session(), Payment.Method.CASH, at, operator, false, true);
        assertEquals(0, covered.getAmountCents());
        assertEquals(Payment.Method.PASS, covered.getMethod());

        Payment lost = periods.pay(session(), Payment.Method.MPESA, at, operator, true, true);
        assertEquals(50000, lost.getAmountCents());
        assertEquals(50000, lost.getLostTicketCents());
        assertEquals(Payment.Method.MPESA, lost.getMethod());

        Payment both = periods.pay(session(), Payment.Method.CARD, at, operator, true, false);
        assertEquals(12000 + 50000, both.getAmountCents());
        assertEquals(50000, both.getLostTicketCents());
    }

    @Test
    void tariffRejectsValuesOutOfRange() {
        assertThrows(IllegalArgumentException.class, () -> new Tariff(15, 15, 6000, 40000, -1, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new Tariff(15, 15, 6000, 40000, 3000, 101, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new Tariff(15, 15, 6000, 40000, 3000, 0, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> new Tariff(15, 15, 6000, 40000, 3000, 0, 0, -1));
    }

    // ---- auxiliares -----------------------------------------------------

    private static ParkingSession session() {
        Vehicle vehicle = new Vehicle("AAA001MC", Vehicle.Type.CAR, null);
        return new ParkingSession(1, vehicle, "L01", StayClass.SHORT, 60, ENTRY);
    }
}
