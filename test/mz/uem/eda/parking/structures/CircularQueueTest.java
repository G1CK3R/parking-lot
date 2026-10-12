package mz.uem.eda.parking.structures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import mz.uem.eda.parking.structures.exceptions.FullStructureException;

class CircularQueueTest extends QueueContractTest {

    @Override
    Queue<String> newQueue() {
        return new CircularQueue<>(CAPACITY);
    }

    // ---- construction -----------------------------------------------------

    @ParameterizedTest
    @ValueSource(ints = {0, -1, Integer.MIN_VALUE})
    void rejectsCapacityBelowOne(int capacity) {
        assertThrows(IllegalArgumentException.class, () -> new CircularQueue<String>(capacity));
    }

    @Test
    void capacityOneIsValid() {
        Queue<String> queue = new CircularQueue<>(1);
        for (String element : List.of("A", "B", "C")) {
            queue.enqueue(element);
            assertTrue(queue.isFull());
            assertThrows(FullStructureException.class, () -> queue.enqueue("X"));
            assertEquals(element, queue.dequeue());
            assertTrue(queue.isEmpty());
        }
    }

    // ---- full queue -------------------------------------------------------

    @Test
    void becomesFullAtCapacity() {
        Queue<String> queue = new CircularQueue<>(3);
        queue.enqueue("A");
        queue.enqueue("B");
        assertFalse(queue.isFull());
        queue.enqueue("C");
        assertTrue(queue.isFull());
        assertEquals(3, queue.size());
    }

    @Test
    void enqueueOnFullThrowsAndLeavesQueueUnchanged() {
        Queue<String> queue = new CircularQueue<>(2);
        queue.enqueue("A");
        queue.enqueue("B");
        assertThrows(FullStructureException.class, () -> queue.enqueue("C"));
        assertEquals(2, queue.size());
        assertEquals("A", queue.peek());
        assertEquals(List.of("A", "B"), toList(queue));
    }

    @Test
    void dequeueFromFullFreesOneSlot() {
        Queue<String> queue = new CircularQueue<>(2);
        queue.enqueue("A");
        queue.enqueue("B");
        queue.dequeue();
        assertFalse(queue.isFull());
        queue.enqueue("C");
        assertTrue(queue.isFull());
        assertEquals("B", queue.dequeue());
        assertEquals("C", queue.dequeue());
    }

    @Test
    void nullOnFullQueueThrowsNullPointer() {
        Queue<String> queue = new CircularQueue<>(1);
        queue.enqueue("A");
        assertThrows(NullPointerException.class, () -> queue.enqueue(null));
    }

    // ---- wrap-around ------------------------------------------------------

    @Test
    void keepsFifoOrderAcrossWrapAround() {
        Queue<Integer> queue = new CircularQueue<>(3);
        queue.enqueue(1);
        queue.enqueue(2);
        queue.enqueue(3);
        assertEquals(1, queue.dequeue());
        assertEquals(2, queue.dequeue());
        queue.enqueue(4); // written at index 0
        queue.enqueue(5); // written at index 1
        assertTrue(queue.isFull());
        assertEquals(3, queue.peek());
        assertEquals(3, queue.dequeue());
        assertEquals(4, queue.dequeue());
        assertEquals(5, queue.dequeue());
        assertTrue(queue.isEmpty());
    }

    @Test
    void iteratesFrontToBackAcrossWrapAround() {
        Queue<Integer> queue = new CircularQueue<>(4);
        for (int i = 0; i < 4; i++) {
            queue.enqueue(i);
        }
        queue.dequeue();
        queue.dequeue();
        queue.enqueue(4);
        queue.enqueue(5);
        assertEquals(List.of(2, 3, 4, 5), toList(queue));
    }

    @Test
    void manyLapsAroundTheArrayKeepFifoOrder() {
        Queue<Integer> queue = new CircularQueue<>(5);
        int nextIn = 0;
        int nextOut = 0;
        // Keep the queue between 2 and 4 elements so the front and the back
        // both lap the array many times.
        for (int i = 0; i < 4; i++) {
            queue.enqueue(nextIn++);
        }
        for (int step = 0; step < 100; step++) {
            assertEquals(nextOut++, queue.dequeue());
            assertEquals(nextOut++, queue.dequeue());
            queue.enqueue(nextIn++);
            queue.enqueue(nextIn++);
            assertEquals(4, queue.size());
            assertEquals(nextOut, queue.peek());
        }
        while (!queue.isEmpty()) {
            assertEquals(nextOut++, queue.dequeue());
        }
        assertEquals(nextIn, nextOut);
    }

    @Test
    void fillAndDrainFromEveryStartingSlot() {
        int capacity = 4;
        Queue<Integer> queue = new CircularQueue<>(capacity);
        for (int offset = 0; offset < capacity; offset++) {
            queue.enqueue(-1); // moves the front one slot further each round
            queue.dequeue();
            for (int i = 0; i < capacity; i++) {
                queue.enqueue(i);
            }
            assertTrue(queue.isFull());
            assertEquals(List.of(0, 1, 2, 3), toList(queue));
            for (int i = 0; i < capacity; i++) {
                assertEquals(i, queue.dequeue());
            }
            assertTrue(queue.isEmpty());
        }
    }
}
