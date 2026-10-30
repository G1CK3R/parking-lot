package mz.uem.eda.parking.service.reports;

/**
 * Uma linha de um total do relatório: um dia, um operador ou um método de
 * pagamento, com quantos pagamentos teve e quanto somaram.
 *
 * <p>Imutável. A chave é o que identifica a linha: um
 * {@link java.time.LocalDate} nos totais por dia, o nome do operador nos
 * totais por operador, um {@link mz.uem.eda.parking.model.Payment.Method}
 * nos totais por método. Os valores são em centavos, como em todo o
 * programa.
 *
 * @param <K> o tipo da chave
 */
public final class RevenueLine<K> {

    private final K key;
    private final int count;
    private final long totalCents;

    public RevenueLine(K key, int count, long totalCents) {
        this.key = key;
        this.count = count;
        this.totalCents = totalCents;
    }

    /**
     * @return o dia, o operador ou o método desta linha
     * @complexity O(1)
     */
    public K getKey() {
        return key;
    }

    /**
     * @return quantos pagamentos
     * @complexity O(1)
     */
    public int getCount() {
        return count;
    }

    /**
     * @return quanto somaram, em centavos
     * @complexity O(1)
     */
    public long getTotalCents() {
        return totalCents;
    }
}
