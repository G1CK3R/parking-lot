package mz.uem.eda.parking.algorithms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import mz.uem.eda.parking.structures.CircularQueue;
import mz.uem.eda.parking.structures.LinkedQueue;
import mz.uem.eda.parking.structures.Queue;

class QueueRadixSortTest {

    private static final IntegerKey KEY = new IntegerKey();

    // ---- trivial inputs ---------------------------------------------------

    @Test
    void emptyQueueStaysEmpty() {
        Queue<Integer> queue = new LinkedQueue<>();
        QueueRadixSort.sort(queue, KEY);
        assertTrue(queue.isEmpty());
    }

    @Test
    void singleElementIsUnchanged() {
        Queue<Integer> queue = queueOf(42);
        QueueRadixSort.sort(queue, KEY);
        assertEquals(List.of(42), toList(queue));
    }

    @Test
    void allZerosAreKept() {
        // max = 0, so no pass runs: the queue must still be intact
        Queue<Integer> queue = queueOf(0, 0, 0);
        QueueRadixSort.sort(queue, KEY);
        assertEquals(List.of(0, 0, 0), toList(queue));
    }

    // ---- ordering ---------------------------------------------------------

    @Test
    void sortsSingleDigitKeys() {
        Queue<Integer> queue = queueOf(3, 1, 4, 1, 5, 9, 2, 6);
        QueueRadixSort.sort(queue, KEY);
        assertEquals(List.of(1, 1, 2, 3, 4, 5, 6, 9), toList(queue));
    }

    @Test
    void sortsKeysWithDifferentNumberOfDigits() {
        Queue<Integer> queue = queueOf(170, 45, 75, 90, 802, 24, 2, 66);
        QueueRadixSort.sort(queue, KEY);
        assertEquals(List.of(2, 24, 45, 66, 75, 90, 170, 802), toList(queue));
    }

    @Test
    void handlesZeroAmongOtherKeys() {
        Queue<Integer> queue = queueOf(10, 0, 100, 1);
        QueueRadixSort.sort(queue, KEY);
        assertEquals(List.of(0, 1, 10, 100), toList(queue));
    }

    @Test
    void handlesKeysThatDifferOnlyInInnerZeros() {
        Queue<Integer> queue = queueOf(1001, 101, 1010, 11, 1100, 110);
        QueueRadixSort.sort(queue, KEY);
        assertEquals(List.of(11, 101, 110, 1001, 1010, 1100), toList(queue));
    }

    @Test
    void alreadySortedStaysSorted() {
        Queue<Integer> queue = queueOf(1, 2, 3, 40, 500);
        QueueRadixSort.sort(queue, KEY);
        assertEquals(List.of(1, 2, 3, 40, 500), toList(queue));
    }

    @Test
    void reverseSortedIsSorted() {
        Queue<Integer> queue = queueOf(500, 40, 3, 2, 1);
        QueueRadixSort.sort(queue, KEY);
        assertEquals(List.of(1, 2, 3, 40, 500), toList(queue));
    }

    @Test
    void handlesIntegerMaxValue() {
        // the pass counter is a long, so 10 digits must not overflow
        Queue<Integer> queue = queueOf(Integer.MAX_VALUE, 0, 1_000_000_000, 7);
        QueueRadixSort.sort(queue, KEY);
        assertEquals(List.of(0, 7, 1_000_000_000, Integer.MAX_VALUE), toList(queue));
    }

    @Test
    void matchesJavaSortOnRandomInput() {
        Random random = new Random(2026);
        for (int round = 0; round < 20; round++) {
            List<Integer> values = new ArrayList<>();
            int n = random.nextInt(200);
            for (int i = 0; i < n; i++) {
                values.add(random.nextInt(1_000_000));
            }
            Queue<Integer> queue = new LinkedQueue<>();
            values.forEach(queue::enqueue);

            QueueRadixSort.sort(queue, KEY);

            List<Integer> expected = new ArrayList<>(values);
            expected.sort(null);
            assertEquals(expected, toList(queue));
        }
    }

    @Test
    void sortsInPlaceOnBoundedQueue() {
        // the same queue instance is refilled, so a full CircularQueue works
        Queue<Integer> queue = new CircularQueue<>(5);
        for (int v : new int[] {50, 4, 300, 2, 10}) {
            queue.enqueue(v);
        }
        QueueRadixSort.sort(queue, KEY);
        assertEquals(List.of(2, 4, 10, 50, 300), toList(queue));
        assertEquals(5, queue.size());
    }

    // ---- stability --------------------------------------------------------

    @Test
    void isStable() {
        // tickets with the same key must keep their arrival order
        Queue<Ticket> queue = new LinkedQueue<>();
        Ticket a = new Ticket(12, "A");
        Ticket b = new Ticket(5, "B");
        Ticket c = new Ticket(12, "C");
        Ticket d = new Ticket(5, "D");
        Ticket e = new Ticket(12, "E");
        for (Ticket t : List.of(a, b, c, d, e)) {
            queue.enqueue(t);
        }

        QueueRadixSort.sort(queue, Ticket::key);

        assertSame(b, queue.dequeue());
        assertSame(d, queue.dequeue());
        assertSame(a, queue.dequeue());
        assertSame(c, queue.dequeue());
        assertSame(e, queue.dequeue());
    }

    // ---- invalid keys -----------------------------------------------------

    @Test
    void negativeKeyThrowsAndLeavesQueueIntact() {
        Queue<Integer> queue = queueOf(5, -3, 8);
        assertThrows(IllegalArgumentException.class, () -> QueueRadixSort.sort(queue, KEY));
        assertEquals(List.of(5, -3, 8), toList(queue));
    }

    // ---- helpers ----------------------------------------------------------

    private record Ticket(int key, String label) {
    }

    private static Queue<Integer> queueOf(int... values) {
        Queue<Integer> queue = new LinkedQueue<>();
        for (int v : values) {
            queue.enqueue(v);
        }
        return queue;
    }

    private static <T> List<T> toList(Iterable<T> iterable) {
        List<T> list = new ArrayList<>();
        for (T element : iterable) {
            list.add(element);
        }
        return list;
    }
}
