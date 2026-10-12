package mz.uem.eda.parking.structures;

import mz.uem.eda.parking.structures.exceptions.EmptyStructureException;
import mz.uem.eda.parking.structures.exceptions.FullStructureException;

/**
 * Last-in, first-out collection: the element added last is the first to
 * leave. Models a dead-end lane, where cars enter and leave through the
 * same end, and the buffer lane used to reach a car at depth k.
 *
 * <p>Only the top is visible. The single exception is the read-only
 * traversal inherited from {@link Iterable}, which goes from the top to
 * the bottom without removing anything.
 *
 * <p>Implementations may be bounded ({@link ArrayStack}) or unbounded
 * ({@link LinkedStack}). Null elements are never accepted. Not thread-safe:
 * the domain touches stacks only on the Swing event thread.
 *
 * @param <T> element type
 */
public interface Stack<T> extends Iterable<T> {

    /**
     * Places an element on top of the stack. After the call it is the
     * element returned by {@link #peek()} and the size has grown by one.
     *
     * @param element the element to add, never null
     * @throws NullPointerException if {@code element} is null
     * @throws FullStructureException if the stack is bounded and full
     * @complexity O(1)
     */
    void push(T element);

    /**
     * Removes and returns the element on top of the stack, the one added
     * most recently. The size shrinks by one.
     *
     * @return the element that was on top
     * @throws EmptyStructureException if the stack holds no elements
     * @complexity O(1)
     */
    T pop();

    /**
     * Returns the element on top of the stack without removing it.
     *
     * @return the element on top
     * @throws EmptyStructureException if the stack holds no elements
     * @complexity O(1)
     */
    T peek();

    /**
     * @return true if the stack holds no elements
     * @complexity O(1)
     */
    boolean isEmpty();

    /**
     * @return the number of elements currently held
     * @complexity O(1)
     */
    int size();

    /**
     * Tells whether another {@link #push(Object)} would fail. An unbounded
     * implementation always returns false.
     *
     * @return true if the stack has reached its capacity
     * @complexity O(1)
     */
    boolean isFull();
}