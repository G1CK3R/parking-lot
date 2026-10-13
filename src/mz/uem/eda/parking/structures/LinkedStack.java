package mz.uem.eda.parking.structures;

import java.util.Iterator;
import java.util.NoSuchElementException;

import mz.uem.eda.parking.structures.exceptions.EmptyStructureException;

/**
 * Pilha de capacidade ilimitada sobre uma lista simplesmente ligada. Usada
 * na pilha de desfazer do operador e na pilha de transacções do turno, cuja
 * profundidade não se conhece à partida: cada elemento custa um nó e a pilha
 * nunca enche.
 *
 * <p>A cabeça da lista é o topo da pilha, por isso {@code push} e
 * {@code pop} só mexem no primeiro nó. A lista ligada é um pormenor de
 * implementação: o domínio só vê as operações de {@link Stack}.
 *
 * @param <T> tipo dos elementos
 */
public class LinkedStack<T> implements Stack<T> {

    private static class Node<T> {
        final T value;
        final Node<T> next;

        Node(T value, Node<T> next) {
            this.value = value;
            this.next = next;
        }
    }

    /** Primeiro nó da lista e topo da pilha; {@code null} quando vazia. */
    private Node<T> top;

    /** Número de elementos guardados, mantido para {@link #size()} ser O(1). */
    private int size;

    /**
     * Cria uma pilha vazia.
     *
     * @complexity O(1)
     */
    public LinkedStack() {
        // a pilha começa sem nós: top == null e size == 0
    }

    /**
     * Coloca um elemento no topo da pilha. Nunca falha por falta de espaço.
     *
     * @param element o elemento a acrescentar, nunca {@code null}
     * @throws NullPointerException se {@code element} for {@code null}
     * @complexity O(1)
     */
    @Override
    public void push(T element) {
        if (element == null) {
            throw new NullPointerException("element must not be null");
        }
        top = new Node<>(element, top);
        size++;
    }

    /**
     * Retira e devolve o elemento do topo da pilha.
     *
     * @return o elemento que estava no topo
     * @throws EmptyStructureException se a pilha não tiver elementos
     * @complexity O(1)
     */
    @Override
    public T pop() {
        if (isEmpty()) {
            throw new EmptyStructureException();
        }
        T value = top.value;
        top = top.next;
        size--;
        return value;
    }

    /**
     * Devolve o elemento do topo da pilha sem o retirar.
     *
     * @return o elemento do topo
     * @throws EmptyStructureException se a pilha não tiver elementos
     * @complexity O(1)
     */
    @Override
    public T peek() {
        if (isEmpty()) {
            throw new EmptyStructureException();
        }
        return top.value;
    }

    /**
     * Devolve sempre {@code false}: a pilha não tem limite e cresce um nó de
     * cada vez.
     *
     * @return {@code false}
     * @complexity O(1)
     */
    @Override
    public boolean isFull() {
        return false;
    }

    /**
     * Indica quantos elementos a pilha guarda neste momento.
     *
     * @return o número de elementos
     * @complexity O(1)
     */
    @Override
    public int size() {
        return size;
    }

    /**
     * Indica se a pilha está vazia.
     *
     * @return {@code true} se a pilha não tiver elementos
     * @complexity O(1)
     */
    @Override
    public boolean isEmpty() {
        return size == 0;
    }

    /**
     * Percurso só de leitura do topo para o fundo, a ordem pela qual
     * {@link #pop()} devolveria os elementos. {@code remove} não é suportado
     * e lança {@link UnsupportedOperationException}. Não fazer {@code push}
     * nem {@code pop} durante o percurso.
     *
     * @return um iterador sobre os elementos, a começar pelo topo
     * @complexity O(1) para criar; O(n) para um percurso completo
     */
    @Override
    public Iterator<T> iterator() {
        return new Iterator<T>() {
            private Node<T> current = top;

            @Override
            public boolean hasNext() {
                return current != null;
            }

            @Override
            public T next() {
                if (current == null) {
                    throw new NoSuchElementException();
                }
                T value = current.value;
                current = current.next;
                return value;
            }
        };
    }
}
