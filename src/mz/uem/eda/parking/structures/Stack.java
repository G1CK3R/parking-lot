package mz.uem.eda.parking.structures;

import mz.uem.eda.parking.structures.exceptions.EmptyStructureException;
import mz.uem.eda.parking.structures.exceptions.FullStructureException;

/**
 * Colecção LIFO (último a entrar, primeiro a sair): o elemento acrescentado
 * por último é o primeiro a sair. Modela a baia em beco sem saída, onde os
 * carros entram e saem pelo mesmo extremo, e a baia de manobra usada para
 * chegar a um carro à profundidade k.
 *
 * <p>Só o topo é visível. A única excepção é o percurso só de leitura
 * herdado de {@link Iterable}, que vai do topo para o fundo sem retirar
 * nada.
 *
 * <p>As implementações podem ter capacidade limitada ({@link ArrayStack}) ou
 * ilimitada ({@link LinkedStack}). Nunca aceitam elementos {@code null}. Não
 * são seguras entre threads: o domínio só toca nas pilhas a partir da thread
 * de eventos do Swing.
 *
 * @param <T> tipo dos elementos
 */
public interface Stack<T> extends Iterable<T> {

    /**
     * Coloca um elemento no topo da pilha. Depois da chamada, é esse o
     * elemento devolvido por {@link #peek()} e o tamanho aumentou uma unidade.
     *
     * @param element o elemento a acrescentar, nunca {@code null}
     * @throws NullPointerException se {@code element} for {@code null}
     * @throws FullStructureException se a pilha tiver capacidade limitada e
     *         estiver cheia
     * @complexity O(1)
     */
    void push(T element);

    /**
     * Retira e devolve o elemento do topo da pilha, o que foi acrescentado
     * mais recentemente. O tamanho diminui uma unidade.
     *
     * @return o elemento que estava no topo
     * @throws EmptyStructureException se a pilha não tiver elementos
     * @complexity O(1)
     */
    T pop();

    /**
     * Devolve o elemento do topo da pilha sem o retirar.
     *
     * @return o elemento do topo
     * @throws EmptyStructureException se a pilha não tiver elementos
     * @complexity O(1)
     */
    T peek();

    /**
     * Indica se a pilha está vazia.
     *
     * @return {@code true} se a pilha não tiver elementos
     * @complexity O(1)
     */
    boolean isEmpty();

    /**
     * Indica quantos elementos a pilha guarda neste momento.
     *
     * @return o número de elementos
     * @complexity O(1)
     */
    int size();

    /**
     * Indica se mais um {@link #push(Object)} falharia. Uma implementação de
     * capacidade ilimitada devolve sempre {@code false}.
     *
     * @return {@code true} se a pilha tiver atingido a capacidade
     * @complexity O(1)
     */
    boolean isFull();
}
