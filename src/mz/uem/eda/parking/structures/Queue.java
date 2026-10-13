package mz.uem.eda.parking.structures;

import mz.uem.eda.parking.structures.exceptions.EmptyStructureException;
import mz.uem.eda.parking.structures.exceptions.FullStructureException;

/**
 * Colecção FIFO (primeiro a entrar, primeiro a sair): o elemento que espera
 * há mais tempo é o primeiro a sair. Modela a baia atravessável, a fila de
 * espera à entrada, a expiração das reservas e o histórico dos últimos N
 * movimentos.
 *
 * <p>Só a frente é visível. A única excepção é o percurso só de leitura
 * herdado de {@link Iterable}, que vai da frente para a cauda sem retirar
 * nada.
 *
 * <p>As implementações podem ter capacidade limitada ({@link CircularQueue})
 * ou ilimitada ({@link LinkedQueue}). Nunca aceitam elementos {@code null}.
 * Não são seguras entre threads: o domínio só toca nas filas a partir da
 * thread de eventos do Swing.
 *
 * @param <T> tipo dos elementos
 */
public interface Queue<T> extends Iterable<T> {

    /**
     * Acrescenta um elemento na cauda da fila. O tamanho aumenta uma unidade;
     * o elemento só sai depois de todos os que já estavam na fila.
     *
     * @param element o elemento a acrescentar, nunca {@code null}
     * @throws NullPointerException se {@code element} for {@code null}
     * @throws FullStructureException se a fila tiver capacidade limitada e
     *         estiver cheia
     * @complexity O(1)
     */
    void enqueue(T element);

    /**
     * Retira e devolve o elemento da frente da fila, o que foi acrescentado
     * há mais tempo. O tamanho diminui uma unidade.
     *
     * @return o elemento que espera há mais tempo
     * @throws EmptyStructureException se a fila não tiver elementos
     * @complexity O(1)
     */
    T dequeue();

    /**
     * Devolve o elemento da frente da fila sem o retirar.
     *
     * @return o elemento que espera há mais tempo
     * @throws EmptyStructureException se a fila não tiver elementos
     * @complexity O(1)
     */
    T peek();

    /**
     * Indica se a fila está vazia.
     *
     * @return {@code true} se a fila não tiver elementos
     * @complexity O(1)
     */
    boolean isEmpty();

    /**
     * Indica quantos elementos a fila guarda neste momento.
     *
     * @return o número de elementos
     * @complexity O(1)
     */
    int size();

    /**
     * Indica se mais um {@link #enqueue(Object)} falharia. Uma implementação
     * de capacidade ilimitada devolve sempre {@code false}.
     *
     * @return {@code true} se a fila tiver atingido a capacidade
     * @complexity O(1)
     */
    boolean isFull();
}
