package mz.uem.eda.parking.algorithms;

/**
 * Diz ao radix sort de onde vem o número a ordenar de cada elemento.
 *
 * @param <T> o tipo dos elementos ordenados
 */
public interface SortKey<T> {

    /**
     * Devolve a chave numérica do elemento.
     *
     * @param element o elemento
     * @return a chave, nunca negativa
     * @complexity O(1) nas implementações do projecto
     */
    int of(T element);
}
