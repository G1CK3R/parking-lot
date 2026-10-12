package mz.uem.eda.parking.structures;

import java.util.Iterator;
import java.util.NoSuchElementException;

import mz.uem.eda.parking.structures.exceptions.EmptyStructureException;
import mz.uem.eda.parking.structures.exceptions.FullStructureException;

/**
 * Fixed-capacity stack backed by an array. Used for the parking lanes and
 * the buffer lane, whose physical depth is known in advance: memory is
 * allocated once and never grows.
 *
 * <p>The bottom of the stack is index 0 and the top is index
 * {@code size - 1}, so push and pop never shift elements.
 *
 * @param <T> element type
 */
public class ArrayStack<T> implements Stack<T> {

    private final T[] elements;

    /** Number of elements held; the top is at {@code size - 1}. */
    private int size;

    /**
     * Creates an empty stack.
     *
     * @param capacity maximum number of elements, at least 1
     * @throws IllegalArgumentException if {@code capacity} is less than 1
     * @complexity O(n) in the capacity, to allocate the array
     */
    @SuppressWarnings("unchecked")
    public ArrayStack(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("Capacity must be at least 1: " + capacity);
        }
        this.elements = (T[]) new Object[capacity];
    }

    /**
     * Places an element on top of the stack.
     *
     * @param element the element to add, never null
     * @throws NullPointerException if {@code element} is null
     * @throws FullStructureException if the stack already holds
     *         {@code capacity} elements
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
     * Returns the element on top of the stack without removing it.
     *
     * @return the element on top
     * @throws EmptyStructureException if the stack holds no elements
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
     * Removes and returns the element on top of the stack. The freed slot
     * is cleared so the array keeps no reference to a car that has left.
     *
     * @return the element that was on top
     * @throws EmptyStructureException if the stack holds no elements
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
     * @return true if the stack holds no elements
     * @complexity O(1)
     */
    @Override
    public boolean isEmpty() {
        return size == 0;
    }

    /**
     * @return true if the stack holds as many elements as its capacity
     * @complexity O(1)
     */
    @Override
    public boolean isFull() {
        return size == elements.length;
    }

    /**
     * @return the number of elements currently held
     * @complexity O(1)
     */
    @Override
    public int size() {
        return this.size;
    }

    /**
     * Read-only traversal from the top to the bottom, the order in which
     * {@link #pop()} would return the elements. {@code remove} is not
     * supported and throws {@link UnsupportedOperationException}. Do not
     * push or pop while iterating.
     *
     * @return an iterator over the elements, top first
     * @complexity O(1) to create; O(n) for a full traversal
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