package mz.uem.eda.parking.algorithms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;

import mz.uem.eda.parking.structures.ArrayStack;
import mz.uem.eda.parking.structures.LinkedQueue;
import mz.uem.eda.parking.structures.LinkedStack;
import mz.uem.eda.parking.structures.Queue;
import mz.uem.eda.parking.structures.Stack;

class SequentialSearchTest {

    // ---- stacks: position counted from the top ----------------------------

    @Test
    void topOfStackIsPositionZero() {
        Stack<String> lane = stackOf("AAA-111", "BBB-222", "CCC-333"); // CCC on top
        assertEquals(0, SequentialSearch.indexOf(lane, "CCC-333"::equals));
    }

    @Test
    void bottomOfStackIsSizeMinusOne() {
        Stack<String> lane = stackOf("AAA-111", "BBB-222", "CCC-333");
        assertEquals(2, SequentialSearch.indexOf(lane, "AAA-111"::equals));
    }

    @Test
    void positionInStackIsNumberOfCarsAbove() {
        Stack<String> lane = stackOf("A", "B", "C", "D", "E"); // E on top
        assertEquals(3, SequentialSearch.indexOf(lane, "B"::equals));
    }

    @Test
    void worksOnLinkedStack() {
        Stack<String> stack = new LinkedStack<>();
        stack.push("A");
        stack.push("B");
        assertEquals(1, SequentialSearch.indexOf(stack, "A"::equals));
    }

    // ---- queues: position counted from the front --------------------------

    @Test
    void frontOfQueueIsPositionZero() {
        Queue<String> waiting = queueOf("A", "B", "C");
        assertEquals(0, SequentialSearch.indexOf(waiting, "A"::equals));
    }

    @Test
    void positionInQueueIsNumberAhead() {
        Queue<String> waiting = queueOf("A", "B", "C", "D");
        assertEquals(3, SequentialSearch.indexOf(waiting, "D"::equals));
    }

    @Test
    void worksOnCircularQueueAfterWrapAround() {
        Queue<String> history = new LinkedQueue<>();
        history.enqueue("A");
        history.enqueue("B");
        history.enqueue("C");
        history.dequeue();
        history.enqueue("D"); // stored at index 0 of the array
        assertEquals(0, SequentialSearch.indexOf(history, "B"::equals));
        assertEquals(2, SequentialSearch.indexOf(history, "D"::equals));
    }

    // ---- not found / empty ------------------------------------------------

    @Test
    void missingElementGivesMinusOne() {
        assertEquals(-1, SequentialSearch.indexOf(queueOf("A", "B"), "Z"::equals));
    }

    @Test
    void emptyStructureGivesMinusOne() {
        assertEquals(-1, SequentialSearch.indexOf(new LinkedQueue<String>(), s -> true));
        assertEquals(-1, SequentialSearch.indexOf(new ArrayStack<String>(3), s -> true));
    }

    // ---- predicate --------------------------------------------------------

    @Test
    void returnsFirstMatchWhenSeveralMatch() {
        Queue<Integer> tickets = new LinkedQueue<>();
        for (int t : new int[] {7, 12, 18, 24}) {
            tickets.enqueue(t);
        }
        assertEquals(1, SequentialSearch.indexOf(tickets, t -> t % 2 == 0));
    }

    @Test
    void matchesByFieldOfElement() {
        Queue<Car> waiting = new LinkedQueue<>();
        waiting.enqueue(new Car("AAA-111", 1));
        waiting.enqueue(new Car("BBB-222", 2));
        waiting.enqueue(new Car("CCC-333", 3));
        assertEquals(2, SequentialSearch.indexOf(waiting, c -> c.ticket() == 3));
    }

    @Test
    void acceptsPredicateOfSupertype() {
        Predicate<Object> notNull = o -> o != null;
        assertEquals(0, SequentialSearch.indexOf(queueOf("A"), notNull));
    }

    @Test
    void stopsAtFirstMatch() {
        Queue<String> queue = queueOf("A", "B", "C", "D");
        List<String> tested = new ArrayList<>();
        SequentialSearch.indexOf(queue, s -> {
            tested.add(s);
            return s.equals("B");
        });
        assertEquals(List.of("A", "B"), tested);
    }

    // ---- structure is left unchanged --------------------------------------

    @Test
    void stackIsUnchangedAfterSearch() {
        Stack<String> lane = stackOf("A", "B", "C");
        SequentialSearch.indexOf(lane, "A"::equals);
        SequentialSearch.indexOf(lane, "Z"::equals);
        assertEquals(3, lane.size());
        assertEquals(List.of("C", "B", "A"), toList(lane));
    }

    @Test
    void queueIsUnchangedAfterSearch() {
        Queue<String> waiting = queueOf("A", "B", "C");
        SequentialSearch.indexOf(waiting, "C"::equals);
        SequentialSearch.indexOf(waiting, "Z"::equals);
        assertEquals(3, waiting.size());
        assertEquals(List.of("A", "B", "C"), toList(waiting));
    }

    // ---- null arguments ---------------------------------------------------

    @Test
    void nullSourceThrows() {
        assertThrows(NullPointerException.class, () -> SequentialSearch.indexOf(null, x -> true));
    }

    @Test
    void nullPredicateThrows() {
        assertThrows(NullPointerException.class, () -> SequentialSearch.indexOf(queueOf("A"), null));
    }

    @Test
    void nullPredicateThrowsEvenOnEmptySource() {
        assertThrows(NullPointerException.class,
                () -> SequentialSearch.indexOf(new LinkedQueue<String>(), null));
    }

    // ---- helpers ----------------------------------------------------------

    private record Car(String plate, int ticket) {
    }

    private static Stack<String> stackOf(String... bottomToTop) {
        Stack<String> stack = new ArrayStack<>(10);
        for (String s : bottomToTop) {
            stack.push(s);
        }
        return stack;
    }

    private static Queue<String> queueOf(String... frontToBack) {
        Queue<String> queue = new LinkedQueue<>();
        for (String s : frontToBack) {
            queue.enqueue(s);
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
