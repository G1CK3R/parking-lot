package mz.uem.eda.parking.structures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import mz.uem.eda.parking.structures.exceptions.EmptyStructureException;

/**
 * Contract shared by every {@link Queue}: FIFO order, empty-queue errors,
 * null rejection and front-to-back iteration. Subclasses supply the
 * implementation and test what is specific to it.
 */
abstract class QueueContractTest {

    /** Capacity large enough for every test in this class. */
    static final int CAPACITY = 16;

    private Queue<String> queue;

    /** @return a new, empty queue holding at least {@link #CAPACITY} elements */
    abstract Queue<String> newQueue();

    @BeforeEach
    void setUp() {
        queue = newQueue();
    }

    // ---- empty queue ------------------------------------------------------

    @Test
    void newQueueIsEmpty() {
        assertTrue(queue.isEmpty());
        assertEquals(0, queue.size());
        assertFalse(queue.isFull());
    }

    @Test
    void dequeueOnEmptyThrows() {
        assertThrows(EmptyStructureException.class, queue::dequeue);
    }

    @Test
    void peekOnEmptyThrows() {
        assertThrows(EmptyStructureException.class, queue::peek);
    }

    @Test
    void dequeueAfterDrainingThrows() {
        queue.enqueue("A");
        queue.dequeue();
        assertThrows(EmptyStructureException.class, queue::dequeue);
        assertThrows(EmptyStructureException.class, queue::peek);
    }

    @Test
    void iteratorOnEmptyHasNothing() {
        Iterator<String> it = queue.iterator();
        assertFalse(it.hasNext());
        assertThrows(NoSuchElementException.class, it::next);
    }

    // ---- null -------------------------------------------------------------

    @Test
    void enqueueNullThrowsAndLeavesQueueUnchanged() {
        queue.enqueue("A");
        assertThrows(NullPointerException.class, () -> queue.enqueue(null));
        assertEquals(1, queue.size());
        assertEquals("A", queue.peek());
    }

    // ---- enqueue / peek / dequeue -----------------------------------------

    @Test
    void enqueueIncrementsSize() {
        queue.enqueue("A");
        assertFalse(queue.isEmpty());
        assertEquals(1, queue.size());
        queue.enqueue("B");
        assertEquals(2, queue.size());
    }

    @Test
    void peekReturnsFrontWithoutRemoving() {
        queue.enqueue("A");
        queue.enqueue("B");
        assertEquals("A", queue.peek());
        assertEquals("A", queue.peek());
        assertEquals(2, queue.size());
    }

    @Test
    void dequeueReturnsSameInstanceEnqueued() {
        String element = new String("A");
        queue.enqueue(element);
        assertSame(element, queue.dequeue());
    }

    @Test
    void dequeueFollowsFifoOrder() {
        queue.enqueue("A");
        queue.enqueue("B");
        queue.enqueue("C");
        assertEquals("A", queue.dequeue());
        assertEquals("B", queue.dequeue());
        assertEquals("C", queue.dequeue());
        assertTrue(queue.isEmpty());
        assertEquals(0, queue.size());
    }

    @Test
    void interleavedEnqueueAndDequeueKeepFifoOrder() {
        queue.enqueue("A");
        queue.enqueue("B");
        assertEquals("A", queue.dequeue());
        queue.enqueue("C");
        assertEquals("B", queue.peek());
        assertEquals("B", queue.dequeue());
        queue.enqueue("D");
        assertEquals("C", queue.dequeue());
        assertEquals("D", queue.dequeue());
        assertTrue(queue.isEmpty());
    }

    @Test
    void acceptsDuplicates() {
        queue.enqueue("A");
        queue.enqueue("A");
        assertEquals(2, queue.size());
        assertEquals("A", queue.dequeue());
        assertEquals("A", queue.dequeue());
    }

    @Test
    void canBeReusedAfterEmptied() {
        for (int round = 0; round < 3; round++) {
            for (int i = 0; i < CAPACITY; i++) {
                queue.enqueue("E" + i);
            }
            for (int i = 0; i < CAPACITY; i++) {
                assertEquals("E" + i, queue.dequeue());
            }
            assertTrue(queue.isEmpty());
        }
    }

    @Test
    void enqueueAfterEmptiedStartsFresh() {
        queue.enqueue("A");
        queue.dequeue();
        queue.enqueue("B");
        assertEquals(1, queue.size());
        assertEquals("B", queue.peek());
        assertEquals(List.of("B"), toList(queue));
    }

    // ---- iteration --------------------------------------------------------

    @Test
    void iteratesFromFrontToBack() {
        queue.enqueue("A");
        queue.enqueue("B");
        queue.enqueue("C");
        assertEquals(List.of("A", "B", "C"), toList(queue));
    }

    @Test
    void iterationDoesNotModifyQueue() {
        queue.enqueue("A");
        queue.enqueue("B");
        toList(queue);
        assertEquals(2, queue.size());
        assertEquals("A", queue.peek());
    }

    @Test
    void iteratorMatchesDequeueOrder() {
        for (int i = 0; i < 5; i++) {
            queue.enqueue("E" + i);
        }
        List<String> iterated = toList(queue);
        List<String> dequeued = new ArrayList<>();
        while (!queue.isEmpty()) {
            dequeued.add(queue.dequeue());
        }
        assertEquals(dequeued, iterated);
    }

    @Test
    void iteratorThrowsWhenExhausted() {
        queue.enqueue("A");
        Iterator<String> it = queue.iterator();
        assertEquals("A", it.next());
        assertFalse(it.hasNext());
        assertThrows(NoSuchElementException.class, it::next);
    }

    @Test
    void iteratorReflectsStateAfterDequeue() {
        queue.enqueue("A");
        queue.enqueue("B");
        queue.enqueue("C");
        queue.dequeue();
        assertEquals(List.of("B", "C"), toList(queue));
    }

    static <T> List<T> toList(Iterable<T> iterable) {
        List<T> list = new ArrayList<>();
        for (T element : iterable) {
            list.add(element);
        }
        return list;
    }
}
