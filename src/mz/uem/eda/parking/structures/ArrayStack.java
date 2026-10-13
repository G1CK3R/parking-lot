package mz.uem.eda.parking.structures;

import java.util.Iterator;
import java.util.NoSuchElementException;

import mz.uem.eda.parking.structures.exceptions.EmptyStructureException;
import mz.uem.eda.parking.structures.exceptions.FullStructureException;

/**
 * Pilha de capacidade fixa sobre um vector. Usada nas baias de
 * estacionamento e na baia de manobra, cuja profundidade física se conhece
 * à partida: a memória é reservada uma vez e nunca cresce.
 *
 * <p>O fundo da pilha é a posição 0 e o topo é a posição {@code size - 1};
 * por isso {@code push} e {@code pop} nunca deslocam elementos.
 *
 * @param <T> tipo dos elementos
 */
public class ArrayStack<T> implements Stack<T> {

    private final T[] elements;

    /** Número de elementos guardados; o topo está em {@code size - 1}. */
    private int size;

    /**
     * Cria uma pilha vazia.
     *
     * @param capacity número máximo de elementos, pelo menos 1
     * @throws IllegalArgumentException se {@code capacity} for menor que 1
     * @complexity O(n) na capacidade, para reservar o vector
     */
    @SuppressWarnings("unchecked")
    public ArrayStack(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("Capacity must be at least 1: " + capacity);
        }
        this.elements = (T[]) new Object[capacity];
    }

    /**
     * Coloca um elemento no topo da pilha.
     *
     * @param element o elemento a acrescentar, nunca {@code null}
     * @throws NullPointerException se {@code element} for {@code null}
     * @throws FullStructureException se a pilha já guardar {@code capacity}
     *         elementos
     * @complexity O(1)
     */
    @Override
    public void push(T element) {
        if (element == null) {
            throw new NullPointerException("Element must not be null");
        }
        if (isFull()) {
            throw new FullStructureException();
        }
        elements[size++] = element;
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
        return elements[size - 1];
    }

    /**
     * Retira e devolve o elemento do topo da pilha. A posição libertada é
     * limpa, para o vector não guardar referência a um carro que já saiu.
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
        T top = elements[--size];
        elements[size] = null;
        return top;
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
     * Indica se a pilha está cheia.
     *
     * @return {@code true} se a pilha guardar tantos elementos como a sua
     *         capacidade
     * @complexity O(1)
     */
    @Override
    public boolean isFull() {
        return size == elements.length;
    }

    /**
     * Indica quantos elementos a pilha guarda neste momento.
     *
     * @return o número de elementos
     * @complexity O(1)
     */
    @Override
    public int size() {
        return this.size;
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
            private int next = size - 1;

            @Override
            public boolean hasNext() {
                return next >= 0;
            }

            @Override
            public T next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                return elements[next--];
            }
        };
    }
}
