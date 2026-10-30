package mz.uem.eda.parking.service.reports;

import mz.uem.eda.parking.model.Charge;
import mz.uem.eda.parking.structures.Queue;

/**
 * O fecho de caixa de um turno (RF21): o que um operador cobrou desde que
 * entrou, estadias e avenças, com o total por método de pagamento (o que
 * deve estar na gaveta, no M-Pesa, no e-Mola e no terminal do cartão) e os
 * valores do turno do maior para o menor. Quem o calcula é o
 * {@link ReportService}.
 *
 * <p>Os valores do turno são ordenados com duas pilhas, O(n²): um turno tem
 * poucas dezenas de valores, e o número de comparações fica guardado para se
 * ver quanto custou. O relatório do administrador, que pode ter milhares,
 * usa o radix sort, O(d·n).
 */
public final class CashClose {

    private final RevenueReport report;
    private final Queue<Charge> byAmount;
    private final long comparisons;

    public CashClose(RevenueReport report, Queue<Charge> byAmount, long comparisons) {
        this.report = report;
        this.byAmount = byAmount;
        this.comparisons = comparisons;
    }

    /**
     * @return a receita do turno, só deste operador: período, total,
     *         número de valores e totais por método
     * @complexity O(1)
     */
    public RevenueReport getReport() {
        return report;
    }

    /**
     * @return os valores do turno (pagamentos de estadias e avenças), do maior
     *         para o menor, ordenados com duas pilhas
     * @complexity O(1) para obter; O(n) para percorrer
     */
    public Iterable<Charge> chargesByAmount() {
        return byAmount;
    }

    /**
     * @return quantas comparações fez a ordenação com duas pilhas
     * @complexity O(1)
     */
    public long getComparisons() {
        return comparisons;
    }
}
