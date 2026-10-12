package mz.uem.eda.parking.structures;

import java.util.Iterator;
import java.util.NoSuchElementException;

import mz.uem.eda.parking.structures.exceptions.EmptyStructureException;
import mz.uem.eda.parking.structures.exceptions.FullStructureException;

/**
 * Fixed-capacity queue backed by a circular array. Used for the through
 * lanes and for the history of the last N moves: memory never grows and no
 * element is ever shifted.
 *
 * @param <T> element type
 */
public class CircularQueue<T> implements Queue<T> {

    private final T[] elements;

    /** Index of the front element (the next to leave). */
    private int head;

    /** Number of elements held. The back is at {@code (head + size) % capacity}. */
    private int size;

    /**
     * Creates an empty queue.
     *
     * @param capacity maximum number of elements, at least 1
     * @throws IllegalArgumentException if {@code capacity} is less than 1
     * @complexity O(n) in the capacity, to allocate the array
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
     * Adds an element at the back of the queue.
     *
     * @param element the element to add, never null
     * @throws NullPointerException if {@code element} is null
     * @throws FullStructureException if the queue is full
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
     * Removes and returns the element at the front of the queue.
     *
     * @return the element that has waited the longest
     * @throws EmptyStructureException if the queue holds no elements
     * @complexity O(1)
     */
    @Override
    public T dequeue() {
        if (isEmpty()) {
            throw new EmptyStructureException();
        }
        T value = elements[head];
        elements[head] = null; // do not keep a reference to a vehicle that has left
        head = (head + 1) % elements.length;
        size--;
        return value;
    }

    /**
     * Returns the element at the front without removing it.
     *
     * @return the element that has waited the longest
     * @throws EmptyStructureException if the queue holds no elements
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
     * @return true if the queue holds no elements
     * @complexity O(1)
     */
    @Override
    public boolean isEmpty() {
        return size == 0;
    }

    /**
     * @return true if the queue holds as many elements as its capacity
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
        return size;
    }

    /**
     * Read-only traversal from the front to the back, the order in which the
     * elements would leave. {@code remove} is not supported. Do not enqueue
     * or dequeue while iterating.
     *
     * @return an iterator over the elements, front first
     * @complexity O(n) for a full traversal
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