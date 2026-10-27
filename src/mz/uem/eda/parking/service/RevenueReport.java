package mz.uem.eda.parking.service;

import java.time.LocalDate;
import java.time.LocalDateTime;

import mz.uem.eda.parking.model.Payment;
import mz.uem.eda.parking.structures.Queue;

/**
 * O relatório de receita de um período (RF18): os pagamentos do período,
 * em três ordens, e os totais por dia, por operador e por método de
 * pagamento. Quem o calcula é o {@link ReportService}.
 *
 * <p>Imutável depois de criado. Por fora só se lê: as filas internas saem
 * como {@link Iterable}, para percorrer sem tirar nada.
 */
public final class RevenueReport {

    private final LocalDateTime from;
    private final LocalDateTime to;
    private final String operator;
    private final int count;
    private final long totalCents;
    private final Queue<Payment> byTime;
    private final Queue<Payment> byAmount;
    private final Queue<Payment> byTicket;
    private final Queue<RevenueLine<LocalDate>> byDay;
    private final Queue<RevenueLine<String>> byOperator;
    private final Queue<RevenueLine<Payment.Method>> byMethod;

    RevenueReport(LocalDateTime from, LocalDateTime to, String operator, long totalCents,
            Queue<Payment> byTime, Queue<Payment> byAmount, Queue<Payment> byTicket,
            Queue<RevenueLine<LocalDate>> byDay, Queue<RevenueLine<String>> byOperator,
            Queue<RevenueLine<Payment.Method>> byMethod) {
        this.from = from;
        this.to = to;
        this.operator = operator;
        this.count = byTime.size();
        this.totalCents = totalCents;
        this.byTime = byTime;
        this.byAmount = byAmount;
        this.byTicket = byTicket;
        this.byDay = byDay;
        this.byOperator = byOperator;
        this.byMethod = byMethod;
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

    /** @return quantos pagamentos houve no período */
    public int getCount() {
        return count;
    }

    /** @return a receita do período, em centavos */
    public long getTotalCents() {
        return totalCents;
    }

    /** @return o valor médio de um pagamento, em centavos (0 sem pagamentos) */
    public long getAverageCents() {
        return count == 0 ? 0 : totalCents / count;
    }

    /** @return os pagamentos pela hora, do mais antigo para o mais recente */
    public Iterable<Payment> paymentsByTime() {
        return byTime;
    }

    /** @return os pagamentos pelo valor, do maior para o menor */
    public Iterable<Payment> paymentsByAmount() {
        return byAmount;
    }

    /** @return os pagamentos pelo número do talão, do menor para o maior */
    public Iterable<Payment> paymentsByTicket() {
        return byTicket;
    }

    /** @return um total por dia com pagamentos, do mais antigo para o mais recente */
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
