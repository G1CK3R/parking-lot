package mz.uem.eda.parking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;

import org.junit.jupiter.api.Test;

import mz.uem.eda.parking.model.Charge;
import mz.uem.eda.parking.model.Lane;
import mz.uem.eda.parking.model.MonthlyPass;
import mz.uem.eda.parking.model.ParkingSession;
import mz.uem.eda.parking.model.Payment;
import mz.uem.eda.parking.model.StayClass;
import mz.uem.eda.parking.model.Tariff;
import mz.uem.eda.parking.model.User;
import mz.uem.eda.parking.model.Vehicle;
import mz.uem.eda.parking.structures.LinkedQueue;
import mz.uem.eda.parking.structures.Queue;

/** Os relatórios de receita, o fecho de caixa e o histórico, sobre dados feitos à mão. */
class ReportServiceTest {

    private static final LocalDateTime DAY1 = LocalDateTime.of(2026, 10, 26, 0, 0);
    private static final LocalDateTime DAY2 = DAY1.plusDays(1);
    private static final LocalDateTime DAY3 = DAY1.plusDays(2);
    private static final Tariff TARIFF = new Tariff(15, 15, 6000, 40000);
    private static final User ANA = user("ana", 2);
    private static final User RUI = user("rui", 3);

    /**
     * O histórico de teste, pela ordem em que foi gravado (que de propósito
     * não é a ordem da hora, para o radix sort ter trabalho):
     * <pre>
     * talão  hora          valor    método  operador
     *   5    dia 1 10:00    75,00   CASH    ana
     *   2    dia 1 09:00   120,00   MPESA   rui
     *   9    dia 2 08:30    60,00   CASH    rui
     *   4    dia 2 18:00   400,00   CARD    ana
     *   7    dia 3 07:00    45,00   EMOLA   ana   (fora do período dia 1 a dia 3)
     * </pre>
     */
    private static Queue<Payment> history() {
        Queue<Payment> history = new LinkedQueue<>();
        history.enqueue(payment(5, DAY1.plusHours(10), 7500, Payment.Method.CASH, ANA));
        history.enqueue(payment(2, DAY1.plusHours(9), 12000, Payment.Method.MPESA, RUI));
        history.enqueue(payment(9, DAY2.plusHours(8).plusMinutes(30), 6000, Payment.Method.CASH, RUI));
        history.enqueue(payment(4, DAY2.plusHours(18), 40000, Payment.Method.CARD, ANA));
        history.enqueue(payment(7, DAY3.plusHours(7), 4500, Payment.Method.EMOLA, ANA));
        return history;
    }

    @Test
    void revenueKeepsOnlyThePeriodAndAddsItUp() {
        RevenueReport report = ReportService.revenue(history(), DAY1, DAY3, null);

        assertEquals(4, report.getCount());
        assertEquals(7500 + 12000 + 6000 + 40000, report.getTotalCents());
        assertEquals((7500 + 12000 + 6000 + 40000) / 4, report.getAverageCents());
    }

    @Test
    void paymentsComeOutInTheThreeOrders() {
        RevenueReport report = ReportService.revenue(history(), DAY1, DAY3, null);

        assertEquals("2 5 9 4", tickets(report.paymentsByTime()));
        assertEquals("2 4 5 9", tickets(report.paymentsByTicket()));
        assertEquals("4 2 5 9", tickets(report.paymentsByAmount()));
    }

    @Test
    void totalsByDayOperatorAndMethod() {
        RevenueReport report = ReportService.revenue(history(), DAY1, DAY3, null);

        StringBuilder days = new StringBuilder();
        for (RevenueLine<LocalDate> line : report.byDay()) {
            days.append(line.getKey().getDayOfMonth()).append('=').append(line.getCount())
                    .append('/').append(line.getTotalCents()).append(' ');
        }
        assertEquals("26=2/19500 27=2/46000 ", days.toString());

        StringBuilder operators = new StringBuilder();
        for (RevenueLine<String> line : report.byOperator()) {
            operators.append(line.getKey()).append('=').append(line.getTotalCents()).append(' ');
        }
        // Pela ordem do id: a ana (2) antes do rui (3).
        assertEquals("ana=47500 rui=18000 ", operators.toString());

        StringBuilder methods = new StringBuilder();
        for (RevenueLine<Payment.Method> line : report.byMethod()) {
            methods.append(line.getKey()).append('=').append(line.getCount()).append(' ');
        }
        // Pela ordem do enum, só os métodos usados no período.
        assertEquals("CASH=2 MPESA=1 CARD=1 ", methods.toString());
    }

    @Test
    void anEmptyPeriodGivesAnEmptyReport() {
        RevenueReport report = ReportService.revenue(history(), DAY3.plusDays(1), DAY3.plusDays(2), null);

        assertEquals(0, report.getCount());
        assertEquals(0, report.getTotalCents());
        assertEquals(0, report.getAverageCents());
        assertEquals("", tickets(report.paymentsByTime()));
        assertTrue(!report.byDay().iterator().hasNext());
    }

    @Test
    void thePeriodMustStartBeforeItEnds() {
        assertThrows(IllegalArgumentException.class, () -> ReportService.revenue(history(), DAY2, DAY2, null));
        assertThrows(IllegalArgumentException.class, () -> ReportService.revenue(history(), DAY2, DAY1, null));
    }

    @Test
    void cashCloseOnlyHasTheOperatorsPaymentsBiggestFirst() {
        CashClose close = ReportService.cashClose(history(), ANA, DAY1, DAY3.plusDays(1));

        RevenueReport report = close.getReport();
        assertEquals("ana", report.getOperator());
        assertEquals(3, report.getCount());
        assertEquals(7500 + 40000 + 4500, report.getTotalCents());
        assertEquals("4 5 7", charges(close.chargesByAmount()));
        assertTrue(close.getComparisons() > 0);
    }

    // ------------------------------------------------------------------
    // Avenças na receita e no fecho de caixa
    // ------------------------------------------------------------------

    /**
     * Duas avenças: uma vendida pela ana no dia 1 (dentro do período) e uma
     * vendida pelo rui no dia 3 (fora do período dia 1 a dia 3).
     */
    private static Queue<MonthlyPass> passes() {
        Queue<MonthlyPass> passes = new LinkedQueue<>();
        passes.enqueue(new MonthlyPass("Maria", null, "BBB100MC", YearMonth.of(2026, 11), 350000,
                Payment.Method.CASH, DAY1.plusHours(11), ANA));
        passes.enqueue(new MonthlyPass("João", "84 000 0000", "CCC200MC", YearMonth.of(2026, 11), 350000,
                Payment.Method.MPESA, DAY3.plusHours(8), RUI));
        return passes;
    }

    @Test
    void revenueAddsThePassesSoldInThePeriod() {
        RevenueReport report = ReportService.revenue(history(), passes(), DAY1, DAY3, null);

        assertEquals(5, report.getCount());
        assertEquals(4, report.getPaymentCount());
        assertEquals(1, report.getPassCount());
        assertEquals(350000, report.getPassTotalCents());
        assertEquals(7500 + 12000 + 6000 + 40000 + 350000, report.getTotalCents());
        // As avenças não entram nas listas dos pagamentos de estadias.
        assertEquals("2 5 9 4", tickets(report.paymentsByTime()));

        StringBuilder days = new StringBuilder();
        for (RevenueLine<LocalDate> line : report.byDay()) {
            days.append(line.getKey().getDayOfMonth()).append('=').append(line.getCount()).append(' ');
        }
        assertEquals("26=3 27=2 ", days.toString());

        StringBuilder operators = new StringBuilder();
        for (RevenueLine<String> line : report.byOperator()) {
            operators.append(line.getKey()).append('=').append(line.getTotalCents()).append(' ');
        }
        assertEquals("ana=397500 rui=18000 ", operators.toString());
    }

    @Test
    void cashCloseHasTheOperatorsPassesToo() {
        CashClose close = ReportService.cashClose(history(), passes(), ANA, DAY1, DAY3.plusDays(1));

        assertEquals(4, close.getReport().getCount());
        assertEquals(7500 + 40000 + 4500 + 350000, close.getReport().getTotalCents());
        assertEquals("BBB100MC 4 5 7", charges(close.chargesByAmount()));
    }

    // ------------------------------------------------------------------
    // Histórico das estadias
    // ------------------------------------------------------------------

    @Test
    void historyJoinsParkedCarsAndPaymentsByEntryTime() {
        Lane lane = Lane.parking("J01", 3, StayClass.FULL_DAY);
        lane.asStack().push(new ParkingSession(11, new Vehicle("DDD300MC", Vehicle.Type.CAR, null), "J01",
                StayClass.FULL_DAY, 60, DAY1.plusHours(9).plusMinutes(30)));
        Queue<Lane> lanes = new LinkedQueue<>();
        lanes.enqueue(lane);

        Queue<HistoryEntry> all = ReportService.history(history(), lanes, DAY1, DAY3, "", null, true, true);
        // Cada pagamento entrou uma hora antes: 2 às 8h, 5 às 9h, o 11 (no parque) às 9h30, e 9 e 4 no dia 2.
        assertEquals("2 5 11 9 4", entries(all));

        Queue<HistoryEntry> onlyParked = ReportService.history(history(), lanes, DAY1, DAY3, "", null, true,
                false);
        assertEquals("11", entries(onlyParked));
        assertTrue(onlyParked.peek().isInPark());

        Queue<HistoryEntry> byPlate = ReportService.history(history(), lanes, DAY1, DAY3, "aaa 9", null, true,
                true);
        assertEquals("9", entries(byPlate));

        // Com operador, só as saídas que ele cobrou; os carros no parque ainda não têm operador.
        Queue<HistoryEntry> ofRui = ReportService.history(history(), lanes, DAY1, DAY3, "", "rui", true, true);
        assertEquals("2 9", entries(ofRui));
    }

    @Test
    void theHistoryIsNotChanged() {
        Queue<Payment> history = history();
        ReportService.revenue(history, DAY1, DAY3, null);
        ReportService.cashClose(history, RUI, DAY1, DAY3);

        assertEquals(5, history.size());
        assertEquals("5 2 9 4 7", tickets(history));
    }

    // ------------------------------------------------------------------

    private static User user(String name, long id) {
        User user = new User(name, "hash", "salt", 1, User.Role.OPERATOR);
        user.setId(id);
        return user;
    }

    private static Payment payment(int ticket, LocalDateTime paidAt, long cents, Payment.Method method,
            User operator) {
        ParkingSession session = new ParkingSession(ticket, new Vehicle("AAA" + ticket + "00MC", Vehicle.Type.CAR, null),
                "J01", StayClass.FULL_DAY, 60, paidAt.minusHours(1));
        session.close(paidAt, 0);
        return new Payment(session, TARIFF, cents, method, paidAt, operator);
    }

    /** Os talões dos pagamentos e as matrículas das avenças, pela ordem dada. */
    private static String charges(Iterable<Charge> charges) {
        StringBuilder text = new StringBuilder();
        for (Charge charge : charges) {
            if (text.length() > 0) {
                text.append(' ');
            }
            if (charge instanceof Payment) {
                text.append(((Payment) charge).getSession().getTicketNumber());
            } else {
                text.append(charge.getPlate());
            }
        }
        return text.toString();
    }

    private static String entries(Iterable<HistoryEntry> entries) {
        StringBuilder text = new StringBuilder();
        for (HistoryEntry entry : entries) {
            if (text.length() > 0) {
                text.append(' ');
            }
            text.append(entry.getSession().getTicketNumber());
        }
        return text.toString();
    }

    private static String tickets(Iterable<Payment> payments) {
        StringBuilder text = new StringBuilder();
        for (Payment payment : payments) {
            if (text.length() > 0) {
                text.append(' ');
            }
            text.append(payment.getSession().getTicketNumber());
        }
        return text.toString();
    }
}
