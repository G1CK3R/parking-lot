package mz.uem.eda.parking.service;

import java.time.LocalDate;
import java.time.LocalDateTime;

import mz.uem.eda.parking.model.MonthlyPass;
import mz.uem.eda.parking.model.Payment;
import mz.uem.eda.parking.structures.Queue;

/**
 * O relatório de receita de um período (RF18): os pagamentos de estadias do
 * período, em três ordens, as avenças vendidas, e os totais por dia, por
 * operador e por método de pagamento. Quem o calcula é o
 * {@link ReportService}.
 *
 * <p>A receita e os totais somam as duas coisas, estadias e avenças; a
 * contagem também. Imutável depois de criado. Por fora só se lê: as filas
 * internas saem como {@link Iterable}, para percorrer sem tirar nada.
 */
public final class RevenueReport {

    private final LocalDateTime from;
    private final LocalDateTime to;
    private final String operator;
    private final long totalCents;
    private final Queue<Payment> byTime;
    private final Queue<Payment> byAmount;
    private final Queue<Payment> byTicket;
    private final Queue<MonthlyPass> passes;
    private final Queue<RevenueLine<LocalDate>> byDay;
    private final Queue<RevenueLine<String>> byOperator;
    private final Queue<RevenueLine<Payment.Method>> byMethod;
    private final long passTotalCents;

    RevenueReport(LocalDateTime from, LocalDateTime to, String operator, long totalCents,
            Queue<Payment> byTime, Queue<Payment> byAmount, Queue<Payment> byTicket, Queue<MonthlyPass> passes,
            Queue<RevenueLine<LocalDate>> byDay, Queue<RevenueLine<String>> byOperator,
            Queue<RevenueLine<Payment.Method>> byMethod) {
        this.from = from;
        this.to = to;
        this.operator = operator;
        this.totalCents = totalCents;
        this.byTime = byTime;
        this.byAmount = byAmount;
        this.byTicket = byTicket;
        this.passes = passes;
        this.byDay = byDay;
        this.byOperator = byOperator;
        this.byMethod = byMethod;
        long sum = 0;
        for (MonthlyPass pass : passes) {
            sum += pass.getAmountCents();
        }
        this.passTotalCents = sum;
    }

    /** @return o início do período, incluído */
    public LocalDateTime getFrom() {
        return from;
    }

    /** @return o fim do período, excluído */
    public LocalDateTime getTo() {
        return to;
    }

    /** @return o operador a que o relatório se limita, ou {@code null} se for de todos */
    public String getOperator() {
        return operator;
    }

    /** @return quantos valores foram cobrados no período: estadias e avenças */
    public int getCount() {
        return byTime.size() + passes.size();
    }

    /** @return quantos pagamentos de estadias houve no período */
    public int getPaymentCount() {
        return byTime.size();
    }

    /** @return quantas avenças foram vendidas no período */
    public int getPassCount() {
        return passes.size();
    }

    /** @return a receita do período, estadias e avenças, em centavos */
    public long getTotalCents() {
        return totalCents;
    }

    /** @return a parte da receita que veio de avenças, em centavos */
    public long getPassTotalCents() {
        return passTotalCents;
    }

    /** @return o valor médio de um valor cobrado, em centavos (0 sem valores) */
    public long getAverageCents() {
        return getCount() == 0 ? 0 : totalCents / getCount();
    }

    /** @return os pagamentos de estadias pela hora, do mais antigo para o mais recente */
    public Iterable<Payment> paymentsByTime() {
        return byTime;
    }

    /** @return os pagamentos de estadias pelo valor, do maior para o menor */
    public Iterable<Payment> paymentsByAmount() {
        return byAmount;
    }

    /** @return os pagamentos de estadias pelo número do talão, do menor para o maior */
    public Iterable<Payment> paymentsByTicket() {
        return byTicket;
    }

    /** @return as avenças vendidas no período, pela hora */
    public Iterable<MonthlyPass> passes() {
        return passes;
    }

    /** @return um total por dia com valores cobrados, do mais antigo para o mais recente */
    public Iterable<RevenueLine<LocalDate>> byDay() {
        return byDay;
    }

    /** @return um total por operador, pela ordem em que os operadores foram criados */
    public Iterable<RevenueLine<String>> byOperator() {
        return byOperator;
    }

    /** @return um total por método de pagamento usado, pela ordem do enum */
    public Iterable<RevenueLine<Payment.Method>> byMethod() {
        return byMethod;
    }
}
