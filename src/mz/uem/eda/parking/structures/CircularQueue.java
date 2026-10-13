package mz.uem.eda.parking.structures;

import java.util.Iterator;
import java.util.NoSuchElementException;

import mz.uem.eda.parking.structures.exceptions.EmptyStructureException;
import mz.uem.eda.parking.structures.exceptions.FullStructureException;

/**
 * Fila de capacidade fixa sobre um vector circular. Usada nas baias
 * atravessáveis e no histórico dos últimos N movimentos: a memória nunca
 * cresce e nenhum elemento é deslocado.
 *
 * @param <T> tipo dos elementos
 */
public class CircularQueue<T> implements Queue<T> {

    private final T[] elements;

    /** Posição do elemento da frente (o próximo a sair). */
    private int head;

    /** Número de elementos guardados. A cauda está em {@code (head + size) % capacity}. */
    private int size;

    /**
     * Cria uma fila vazia.
     *
     * @param capacity número máximo de elementos, pelo menos 1
     * @throws IllegalArgumentException se {@code capacity} for menor que 1
     * @complexity O(n) na capacidade, para reservar o vector
     */
    @SuppressWarnings("unchecked")
    public CircularQueue(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be at least 1: " + capacity);
        }
        this.elements = (T[]) new Object[capacity];
        this.head = 0;
        this.size = 0;
    }

    /**
     * Acrescenta um elemento na cauda da fila.
     *
     * @param element o elemento a acrescentar, nunca {@code null}
     * @throws NullPointerException se {@code element} for {@code null}
     * @throws FullStructureException se a fila estiver cheia
     * @complexity O(1)
     */
    @Override
    public void enqueue(T element) {
        if (element == null) {
            throw new NullPointerException("element must not be null");
        }
        if (isFull()) {
            throw new FullStructureException();
        }
        elements[(head + size) % elements.length] = element;
        size++;
    }

    /**
     * Retira e devolve o elemento da frente da fila.
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
        T value = elements[head];
        elements[head] = null; // não guardar referência a um veículo que já saiu
        head = (head + 1) % elements.length;
        size--;
        return value;
    }

    /**
     * Devolve o elemento da frente sem o retirar.
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
        return elements[head];
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
     * Indica se a fila está cheia.
     *
     * @return {@code true} se a fila guardar tantos elementos como a sua
     *         capacidade
     * @complexity O(1)
     */
    @Override
    public boolean isFull() {
        return size == elements.length;
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
     * Percurso só de leitura da frente para a cauda, a ordem pela qual os
     * elementos sairiam. {@code remove} não é suportado. Não fazer
     * {@code enqueue} nem {@code dequeue} durante o percurso.
     *
     * @return um iterador sobre os elementos, a começar pela frente
     * @complexity O(1) para criar; O(n) para um percurso completo
     */
    @Override
    public Iterator<T> iterator() {
        return new Iterator<T>() {
            private int visited = 0;

            @Override
            public boolean hasNext() {
                return visited < size;
            }

            @Override
            public T next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                return elements[(head + visited++) % elements.length];
            }
        };
    }
}
