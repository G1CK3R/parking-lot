package mz.uem.eda.parking.structures;

import mz.uem.eda.parking.structures.exceptions.EmptyStructureException;
import mz.uem.eda.parking.structures.exceptions.FullStructureException;

/**
 * First-in, first-out collection: the element that has waited the longest
 * is the first to leave. Models a through lane, the waiting line at the
 * entrance, reservation expiry and the history of the last N moves.
 *
 * <p>Only the front is visible. The single exception is the read-only
 * traversal inherited from {@link Iterable}, which goes from the front to
 * the back without removing anything.
 *
 * <p>Implementations may be bounded ({@link CircularQueue}) or unbounded
 * ({@link LinkedQueue}). Null elements are never accepted. Not thread-safe:
 * the domain touches queues only on the Swing event thread.
 *
 * @param <T> element type
 */
public interface Queue<T> extends Iterable<T> {

    /**
     * Adds an element at the back of the queue. The size grows by one; the
     * element leaves only after every element already in the queue.
     *
     * @param element the element to add, never null
     * @throws NullPointerException if {@code element} is null
     * @throws FullStructureException if the queue is bounded and full
     * @complexity O(1)
     */
    void enqueue(T element);

    /**
     * Removes and returns the element at the front of the queue, the one
     * added earliest. The size shrinks by one.
     *
     * @return the element that has waited the longest
     * @throws EmptyStructureException if the queue holds no elements
     * @complexity O(1)
     */
    T dequeue();

    /**
     * Returns the element at the front without removing it.
     *
     * @return the element that has waited the longest
     * @throws EmptyStructureException if the queue holds no elements
     * @complexity O(1)
     */
    T peek();

    /**
     * @return true if the queue holds no elements
     * @complexity O(1)
     */
    boolean isEmpty();

    /**
     * @return the number of elements currently held
     * @complexity O(1)
     */
    int size();

    /**
     * Tells whether another {@link #enqueue(Object)} would fail. An
     * unbounded implementation always returns false.
     *
     * @return true if the queue has reached its capacity
     * @complexity O(1)
     */
    boolean isFull();
}