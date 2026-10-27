package mz.uem.eda.parking.service.billing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDateTime;
import java.time.YearMonth;

import org.junit.jupiter.api.Test;

import mz.uem.eda.parking.model.MonthlyPass;
import mz.uem.eda.parking.model.Payment;
import mz.uem.eda.parking.model.Tariff;
import mz.uem.eda.parking.model.User;

/** As avenças mensais (RF16): venda, validade e recusas. */
class MonthlyPassServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 27, 10, 0);
    private static final YearMonth OCTOBER = YearMonth.of(2026, 10);
    private static final Tariff TARIFF = new Tariff(15, 15, 6000, 40000, 3000, 20, 50000, 350000);
    private static final User ANA = new User("ana", "hash", "salt", 1, User.Role.OPERATOR);

    @Test
    void sellUsesTheTariffValueAndCoversTheWholeMonth() {
        MonthlyPassService passes = new MonthlyPassService();
        MonthlyPass pass = passes.sell("Maria", " 84 000 0000 ", "bbb-100-mc", OCTOBER, Payment.Method.CASH, NOW,
                ANA, TARIFF);

        assertEquals(350000, pass.getAmountCents());
        assertEquals("BBB100MC", pass.getPlate());
        assertEquals("84 000 0000", pass.getPhone());
        assertEquals(OCTOBER.atDay(1), pass.getValidFrom());
        assertEquals(OCTOBER.atEndOfMonth(), pass.getValidUntil());
        assertEquals(1, passes.size());

        assertSame(pass, passes.validFor("BBB 100 MC", LocalDateTime.of(2026, 10, 1, 0, 0)));
        assertSame(pass, passes.validFor("BBB100MC", LocalDateTime.of(2026, 10, 31, 23, 59)));
        assertNull(passes.validFor("BBB100MC", LocalDateTime.of(2026, 11, 1, 0, 0)));
        assertNull(passes.validFor("CCC100MC", NOW));
    }

    @Test
    void aPassForNextMonthOnlyCountsThen() {
        MonthlyPassService passes = new MonthlyPassService();
        passes.sell("Maria", null, "BBB100MC", OCTOBER.plusMonths(1), Payment.Method.MPESA, NOW, ANA, TARIFF);

        assertNull(passes.validFor("BBB100MC", NOW));
        assertEquals(OCTOBER.plusMonths(1), passes.validFor("BBB100MC", NOW.plusDays(10)).getMonth());
    }

    @Test
    void refusesPastMonthsRepeatsAndBadValues() {
        MonthlyPassService passes = new MonthlyPassService();
        passes.sell("Maria", null, "BBB100MC", OCTOBER, Payment.Method.CASH, NOW, ANA, TARIFF);

        assertThrows(IllegalArgumentException.class,
                () -> passes.sell("Maria", null, "BBB100MC", OCTOBER.minusMonths(1), Payment.Method.CASH, NOW, ANA,
                        TARIFF));
        assertThrows(IllegalStateException.class,
                () -> passes.sell("Outra", null, "bbb 100 mc", OCTOBER, Payment.Method.CASH, NOW, ANA, TARIFF));
        assertThrows(IllegalArgumentException.class,
                () -> passes.sell(" ", null, "DDD100MC", OCTOBER, Payment.Method.CASH, NOW, ANA, TARIFF));
        assertThrows(IllegalArgumentException.class,
                () -> passes.sell("Maria", null, " - ", OCTOBER, Payment.Method.CASH, NOW, ANA, TARIFF));
        assertThrows(IllegalArgumentException.class,
                () -> passes.sell("Maria", null, "DDD100MC", OCTOBER, Payment.Method.PASS, NOW, ANA, TARIFF));
        assertEquals(1, passes.size());
    }

    @Test
    void loadKeepsTheSaleOrder() {
        MonthlyPassService passes = new MonthlyPassService();
        passes.load(new MonthlyPass("A", null, "AAA100MC", OCTOBER, 1, Payment.Method.CASH, NOW, ANA));
        passes.load(new MonthlyPass("B", null, "BBB100MC", OCTOBER, 2, Payment.Method.CASH, NOW, ANA));

        StringBuilder order = new StringBuilder();
        for (MonthlyPass pass : passes.passes()) {
            order.append(pass.getCustomerName());
        }
        assertEquals("AB", order.toString());
        assertEquals("BBB100MC", passes.find("bbb100mc", OCTOBER).getPlate());
        assertNull(passes.find("BBB100MC", OCTOBER.plusMonths(1)));
    }
}
