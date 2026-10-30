package mz.uem.eda.parking.algorithms;

/**
 * Chave de ordenação para inteiros: a chave é o próprio número.
 */
public class IntegerKey implements SortKey<Integer> {

    /**
     * Devolve o próprio inteiro como chave.
     *
     * @param element o inteiro
     * @return o mesmo valor
     * @complexity O(1)
     */
    @Override
    public int of(Integer element) {
        return element;
    }
}
