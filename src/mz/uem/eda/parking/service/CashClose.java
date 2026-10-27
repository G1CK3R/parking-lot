package mz.uem.eda.parking.service;

import mz.uem.eda.parking.model.Payment;
import mz.uem.eda.parking.structures.Queue;

/**
 * O fecho de caixa de um turno (RF21): o que um operador cobrou desde que
 * entrou, com o total por método de pagamento (o que deve estar na gaveta,
 * no M-Pesa, no e-Mola e no terminal do cartão) e os pagamentos do turno
 * do maior para o menor. Quem o calcula é o {@link ReportService}.
 *
 * <p>Os pagamentos do turno são ordenados com duas pilhas, O(n²): um turno
 * tem poucas dezenas de pagamentos, e o número de comparações fica
 * guardado para se ver quanto custou. O relatório do administrador, que
 * pode ter milhares, usa o radix sort, O(d·n).
 */
public final class CashClose {

    private final RevenueReport report;
    private final Queue<Payment> byAmount;
    private final long comparisons;

    CashClose(RevenueReport report, Queue<Payment> byAmount, long comparisons) {
        this.report = report;
        this.byAmount = byAmount;
        this.comparisons = comparisons;
    }

    /**
     * @return a receita do turno, só deste operador: período, total,
     *         número de pagamentos e totais por método
     */
    public RevenueReport getReport() {
        return report;
    }

    /** @return os pagamentos do turno, do maior para o menor, ordenados com duas pilhas */
    public Iterable<Payment> paymentsByAmount() {
        return byAmount;
    }

    /** @return quantas comparações fez a ordenação com duas pilhas */
    public long getComparisons() {
        return comparisons;
    }
}
