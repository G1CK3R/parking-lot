package mz.uem.eda.parking.structures;

import java.util.Iterator;
import java.util.NoSuchElementException;

import mz.uem.eda.parking.structures.exceptions.EmptyStructureException;

/**
 * Unbounded stack backed by a singly linked list. Used for the operator's
 * undo stack and the shift's transaction stack, whose depth is not known
 * in advance: each element costs one node, and the stack never fills.
 *
 * <p>The head of the list is the top of the stack, so push and pop only
 * touch the first node. The linked list is an implementation detail: the
 * domain sees only the {@link Stack} operations.
 *
 * @param <T> element type
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

    /** First node of the list and top of the stack; null when empty. */
    private Node<T> top;

    /** Number of elements held, kept so that {@link #size()} is O(1). */
    private int size;

    /**
     * Places an element on top of the stack. Never fails for lack of space.
     *
     * @param element the element to add, never null
     * @throws NullPointerException if {@code element} is null
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
     * Removes and returns the element on top of the stack.
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
        T value = top.value;
        top = top.next;
        size--;
        return value;
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
        return top.value;
    }

    /**
     * Always false: the stack is unbounded and grows one node at a time.
     *
     * @return false
     * @complexity O(1)
     */
    @Override
    public boolean isFull() {
        return false;
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
     * @return true if the stack holds no elements
     * @complexity O(1)
     */
    @Override
    public boolean isEmpty() {
        return size == 0;
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