package mz.uem.eda.parking.algorithms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import mz.uem.eda.parking.structures.CircularQueue;
import mz.uem.eda.parking.structures.LinkedQueue;
import mz.uem.eda.parking.structures.Queue;

class QueueMergeTest {

    private static final IntegerKey KEY = new IntegerKey();

    // ---- empty inputs -----------------------------------------------------

    @Test
    void bothEmptyGiveEmptyResult() {
        Queue<Integer> result = QueueMerge.merge(queueOf(), queueOf(), KEY);
        assertTrue(result.isEmpty());
    }

    @Test
    void firstEmptyGivesSecond() {
        Queue<Integer> result = QueueMerge.merge(queueOf(), queueOf(1, 4, 9), KEY);
        assertEquals(List.of(1, 4, 9), toList(result));
    }

    @Test
    void secondEmptyGivesFirst() {
        Queue<Integer> result = QueueMerge.merge(queueOf(2, 3, 7), queueOf(), KEY);
        assertEquals(List.of(2, 3, 7), toList(result));
    }

    // ---- ordering ---------------------------------------------------------

    @Test
    void interleavesTwoSortedQueues() {
        Queue<Integer> result = QueueMerge.merge(queueOf(1, 3, 5, 7), queueOf(2, 4, 6, 8), KEY);
        assertEquals(List.of(1, 2, 3, 4, 5, 6, 7, 8), toList(result));
    }

    @Test
    void handlesQueuesOfDifferentLengths() {
        Queue<Integer> result = QueueMerge.merge(queueOf(5), queueOf(1, 2, 3, 8, 9), KEY);
        assertEquals(List.of(1, 2, 3, 5, 8, 9), toList(result));
    }

    @Test
    void firstEntirelyBeforeSecond() {
        Queue<Integer> result = QueueMerge.merge(queueOf(1, 2, 3), queueOf(10, 20), KEY);
        assertEquals(List.of(1, 2, 3, 10, 20), toList(result));
    }

    @Test
    void secondEntirelyBeforeFirst() {
        Queue<Integer> result = QueueMerge.merge(queueOf(10, 20), queueOf(1, 2, 3), KEY);
        assertEquals(List.of(1, 2, 3, 10, 20), toList(result));
    }

    @Test
    void keepsDuplicates() {
        Queue<Integer> result = QueueMerge.merge(queueOf(1, 2, 2, 5), queueOf(2, 5, 5), KEY);
        assertEquals(List.of(1, 2, 2, 2, 5, 5, 5), toList(result));
    }

    @Test
    void matchesJavaSortOnRandomSortedInput() {
        Random random = new Random(2026);
        for (int round = 0; round < 20; round++) {
            List<Integer> a = randomSorted(random);
            List<Integer> b = randomSorted(random);

            Queue<Integer> result = QueueMerge.merge(queueOf(a), queueOf(b), KEY);

            List<Integer> expected = new ArrayList<>(a);
            expected.addAll(b);
            expected.sort(null);
            assertEquals(expected, toList(result));
        }
    }

    // ---- stability --------------------------------------------------------

    @Test
    void tiesGoToFirstQueue() {
        Shift a1 = new Shift(10, "A1");
        Shift a2 = new Shift(20, "A2");
        Shift b1 = new Shift(10, "B1");
        Shift b2 = new Shift(20, "B2");
        Queue<Shift> first = new LinkedQueue<>();
        first.enqueue(a1);
        first.enqueue(a2);
        Queue<Shift> second = new LinkedQueue<>();
        second.enqueue(b1);
        second.enqueue(b2);

        Queue<Shift> result = QueueMerge.merge(first, second, Shift::time);

        assertSame(a1, result.dequeue());
        assertSame(b1, result.dequeue());
        assertSame(a2, result.dequeue());
        assertSame(b2, result.dequeue());
    }

    // ---- effect on the inputs ---------------------------------------------

    @Test
    void inputQueuesAreEmptiedAndResultIsNew() {
        Queue<Integer> first = queueOf(1, 3);
        Queue<Integer> second = queueOf(2, 4);

        Queue<Integer> result = QueueMerge.merge(first, second, KEY);

        assertTrue(first.isEmpty());
        assertTrue(second.isEmpty());
        assertNotSame(first, result);
        assertNotSame(second, result);
        assertEquals(4, result.size());
    }

    @Test
    void acceptsBoundedQueuesAsInput() {
        Queue<Integer> first = new CircularQueue<>(3);
        first.enqueue(1);
        first.enqueue(4);
        first.enqueue(6);
        Queue<Integer> second = new CircularQueue<>(2);
        second.enqueue(2);
        second.enqueue(5);

        Queue<Integer> result = QueueMerge.merge(first, second, KEY);

        assertEquals(List.of(1, 2, 4, 5, 6), toList(result));
    }

    // ---- helpers ----------------------------------------------------------

    private record Shift(int time, String label) {
    }

    private static List<Integer> randomSorted(Random random) {
        List<Integer> list = new ArrayList<>();
        int n = random.nextInt(50);
        for (int i = 0; i < n; i++) {
            list.add(random.nextInt(1000));
        }
        list.sort(null);
        return list;
    }

    private static Queue<Integer> queueOf(int... values) {
        Queue<Integer> queue = new LinkedQueue<>();
        for (int v : values) {
            queue.enqueue(v);
        }
        return queue;
    }

    private static Queue<Integer> queueOf(List<Integer> values) {
        Queue<Integer> queue = new LinkedQueue<>();
        values.forEach(queue::enqueue);
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
