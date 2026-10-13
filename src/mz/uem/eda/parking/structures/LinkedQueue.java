package mz.uem.eda.parking.structures;

import java.util.Iterator;
import java.util.NoSuchElementException;

import mz.uem.eda.parking.structures.exceptions.EmptyStructureException;

/**
 * Fila de capacidade ilimitada sobre uma lista simplesmente ligada, com
 * referências para as duas pontas. Usada na fila de espera à entrada, cujo
 * comprimento não se conhece à partida: cada elemento custa um nó e a fila
 * nunca enche.
 *
 * <p>Os elementos entram pela cauda e saem pela cabeça, por isso
 * {@code enqueue} e {@code dequeue} só mexem numa ponta cada. A lista ligada
 * é um pormenor de implementação: o domínio só vê as operações de
 * {@link Queue}.
 *
 * @param <T> tipo dos elementos
 */
public class LinkedQueue<T> implements Queue<T> {

    private static class Node<T> {
        final T value;
        Node<T> next;

        Node(T value) {
            this.value = value;
        }
    }

    /** Frente da fila, o próximo a sair; {@code null} quando vazia. */
    private Node<T> head;

    /** Cauda da fila, o último a chegar; {@code null} quando vazia. */
    private Node<T> tail;

    /** Número de elementos guardados, mantido para {@link #size()} ser O(1). */
    private int size;

    /**
     * Cria uma fila vazia.
     *
     * @complexity O(1)
     */
    public LinkedQueue() {
        // a fila começa sem nós: head == tail == null e size == 0
    }

    /**
     * Acrescenta um elemento na cauda da fila. Nunca falha por falta de
     * espaço.
     *
     * @param element o elemento a acrescentar, nunca {@code null}
     * @throws NullPointerException se {@code element} for {@code null}
     * @complexity O(1), graças à referência para a cauda
     */
    @Override
    public void enqueue(T element) {
        if (element == null) {
            throw new NullPointerException("element must not be null");
        }
        Node<T> node = new Node<>(element);
        if (tail == null) {
            head = node; // o primeiro elemento é ao mesmo tempo frente e cauda
        } else {
            tail.next = node;
        }
        tail = node;
        size++;
    }

    /**
     * Retira e devolve o elemento da frente da fila. Quando sai o último
     * elemento, a cauda também é reposta, para o próximo {@code enqueue}
     * começar uma lista nova.
     *
     * @return o elemento que espera há mais tempo
     * @throws EmptyStructureException se a fila não tiver elementos
     * @complexity O(1)
     */
    @Override
    public T dequeue() {
        if (isEmpty()) {
            throw new EmptyStructureException();
        }
        T value = head.value;
        head = head.next;
        if (head == null) {
            tail = null;
        }
        size--;
        return value;
    }

    /**
     * Devolve o elemento da frente da fila sem o retirar.
     *
     * @return o elemento que espera há mais tempo
     * @throws EmptyStructureException se a fila não tiver elementos
     * @complexity O(1)
     */
    @Override
    public T peek() {
        if (isEmpty()) {
            throw new EmptyStructureException();
        }
        return head.value;
    }

    /**
     * Indica se a fila está vazia.
     *
     * @return {@code true} se a fila não tiver elementos
     * @complexity O(1)
     */
    @Override
    public boolean isEmpty() {
        return size == 0;
    }

    /**
     * Indica quantos elementos a fila guarda neste momento.
     *
     * @return o número de elementos
     * @complexity O(1)
     */
    @Override
    public int size() {
        return size;
    }

    /**
     * Devolve sempre {@code false}: a fila não tem limite e cresce um nó de
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
     * Percurso só de leitura da frente para a cauda, a ordem pela qual
     * {@link #dequeue()} devolveria os elementos. {@code remove} não é
     * suportado e lança {@link UnsupportedOperationException}. Não fazer
     * {@code enqueue} nem {@code dequeue} durante o percurso.
     *
     * @return um iterador sobre os elementos, a começar pela frente
     * @complexity O(1) para criar; O(n) para um percurso completo
     */
    @Override
    public Iterator<T> iterator() {
        return new Iterator<T>() {
            private Node<T> current = head;

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
