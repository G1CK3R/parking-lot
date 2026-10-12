package mz.uem.eda.parking.structures;

import java.util.Iterator;
import java.util.NoSuchElementException;

import mz.uem.eda.parking.structures.exceptions.EmptyStructureException;

/**
 * Unbounded queue backed by a singly linked list with references to both
 * ends. Used for the waiting line at the entrance, whose length is not
 * known in advance: each element costs one node, and the queue never fills.
 *
 * <p>Elements enter at the tail and leave at the head, so enqueue and
 * dequeue only touch one end each. The linked list is an implementation
 * detail: the domain sees only the {@link Queue} operations.
 *
 * @param <T> element type
 */
public class LinkedQueue<T> implements Queue<T> {

    private static class Node<T> {
        final T value;
        Node<T> next;

        Node(T value) {
            this.value = value;
        }
    }

    /** Front of the queue, the next to leave; null when empty. */
    private Node<T> head;

    /** Back of the queue, the last to arrive; null when empty. */
    private Node<T> tail;

    /** Number of elements held, kept so that {@link #size()} is O(1). */
    private int size;

    /**
     * Adds an element at the back of the queue. Never fails for lack of
     * space.
     *
     * @param element the element to add, never null
     * @throws NullPointerException if {@code element} is null
     * @complexity O(1), thanks to the reference to the tail
     */
    @Override
    public void enqueue(T element) {
        if (element == null) {
            throw new NullPointerException("element must not be null");
        }
        Node<T> node = new Node<>(element);
        if (tail == null) {
            head = node; // first element is both front and back
        } else {
            tail.next = node;
        }
        tail = node;
        size++;
    }

    /**
     * Removes and returns the element at the front of the queue. When the
     * last element leaves, the tail is reset as well, so the next enqueue
     * starts a fresh list.
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
        T value = head.value;
        head = head.next;
        if (head == null) {
            tail = null;
        }
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
        return head.value;
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
     * @return the number of elements currently held
     * @complexity O(1)
     */
    @Override
    public int size() {
        return size;
    }

    /**
     * Always false: the queue is unbounded and grows one node at a time.
     *
     * @return false
     * @complexity O(1)
     */
    @Override
    public boolean isFull() {
        return false;
    }

    /**
     * Read-only traversal from the front to the back, the order in which
     * {@link #dequeue()} would return the elements. {@code remove} is not
     * supported and throws {@link UnsupportedOperationException}. Do not
     * enqueue or dequeue while iterating.
     *
     * @return an iterator over the elements, front first
     * @complexity O(1) to create; O(n) for a full traversal
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