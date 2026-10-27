package mz.uem.eda.parking.service.billing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import mz.uem.eda.parking.model.ParkingSession;
import mz.uem.eda.parking.model.Payment;
import mz.uem.eda.parking.model.StayClass;
import mz.uem.eda.parking.model.Tariff;
import mz.uem.eda.parking.model.User;
import mz.uem.eda.parking.model.Vehicle;

/** O histórico de pagamentos e a tarifa que cada um guarda (ADR-031). */
class PaymentHistoryServiceTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 10, 22, 8, 0);
    private static final User OPERATOR = new User("op1", "hash", "salt", 1, User.Role.OPERATOR);

    @Test
    void anOldPaymentKeepsTheTariffItWasChargedWith() {
        Tariff old = new Tariff(15, 15, 6000, 40000);
        Tariff newer = new Tariff(10, 30, 8000, 50000);
        BillingService billing = new BillingService(old);
        PaymentHistoryService history = new PaymentHistoryService();

        history.add(payAndLeave(billing, session(1), T0.plusHours(2), 1));
        billing.setTariff(newer);
        history.add(payAndLeave(billing, session(2), T0.plusHours(2), 2));

        Payment first = history.findByTicket(1);
        assertSame(old, first.getTariff());
        assertEquals(12000, first.getAmountCents());                 // 2 h a 60 MZN
        Payment second = history.findByTicket(2);
        assertSame(newer, second.getTariff());
        assertEquals(16000, second.getAmountCents());                // 2 h a 80 MZN
        assertEquals(2, history.size());
    }

    @Test
    void unknownTicketGivesNull() {
        PaymentHistoryService history = new PaymentHistoryService();
        assertNull(history.findByTicket(1));

        history.add(payAndLeave(new BillingService(new Tariff(15, 15, 6000, 40000)), session(1), T0.plusHours(1), 1));
        assertNull(history.findByTicket(2));
    }

    @Test
    void onlySavedPaymentsOfClosedSessionsEnter() {
        PaymentHistoryService history = new PaymentHistoryService();
        BillingService billing = new BillingService(new Tariff(15, 15, 6000, 40000));

        ParkingSession open = session(1);
        Payment unsaved = billing.pay(open, Payment.Method.CASH, T0.plusHours(1), OPERATOR);
        assertThrows(IllegalArgumentException.class, () -> history.add(unsaved));   // sem id

        unsaved.setId(7);
        assertThrows(IllegalArgumentException.class, () -> history.add(unsaved));   // sessão activa
        assertThrows(NullPointerException.class, () -> history.add(null));
        assertEquals(0, history.size());
    }

    @Test
    void searchGoesFromTheOldestPayment() {
        PaymentHistoryService history = new PaymentHistoryService();
        BillingService billing = new BillingService(new Tariff(15, 15, 6000, 40000));
        for (int ticket = 1; ticket <= 5; ticket++) {
            history.add(payAndLeave(billing, session(ticket), T0.plusHours(ticket), ticket));
        }

        assertEquals(5, history.size());
        assertEquals(T0.plusHours(3), history.findByTicket(3).getPaidAt());
        assertEquals(3, history.findByTicket(3).getSession().getTicketNumber());
    }

    // ---- auxiliares -----------------------------------------------------

    /** Paga, fecha a sessão e dá ao pagamento o id que a base daria. */
    private static Payment payAndLeave(BillingService billing, ParkingSession session, LocalDateTime at, long id) {
        Payment payment = billing.pay(session, Payment.Method.CASH, at, OPERATOR);
        session.close(at, 0);
        payment.setId(id);
        return payment;
    }

    private static ParkingSession session(int ticket) {
        Vehicle vehicle = new Vehicle("AAA00" + ticket + "MC", Vehicle.Type.CAR);
        return new ParkingSession(ticket, vehicle, "L01", StayClass.FULL_DAY, 120, T0);
    }
}
