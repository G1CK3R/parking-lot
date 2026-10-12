package mz.uem.eda.parking.structures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class LinkedQueueTest extends QueueContractTest {

    @Override
    Queue<String> newQueue() {
        return new LinkedQueue<>();
    }

    @Test
    void isNeverFull() {
        Queue<Integer> queue = new LinkedQueue<>();
        for (int i = 0; i < 10_000; i++) {
            queue.enqueue(i);
            assertFalse(queue.isFull());
        }
        assertEquals(10_000, queue.size());
        assertEquals(0, queue.peek());
    }

    @Test
    void tailIsResetWhenEmptied() {
        // If the tail kept pointing at a removed node, the next enqueue would
        // be linked after it and lost from the front.
        Queue<String> queue = new LinkedQueue<>();
        queue.enqueue("A");
        queue.enqueue("B");
        queue.dequeue();
        queue.dequeue();
        queue.enqueue("C");
        queue.enqueue("D");
        assertEquals("C", queue.dequeue());
        assertEquals("D", queue.dequeue());
    }
}
