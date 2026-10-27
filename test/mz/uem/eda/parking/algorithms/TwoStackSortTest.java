package mz.uem.eda.parking.algorithms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import mz.uem.eda.parking.structures.ArrayStack;
import mz.uem.eda.parking.structures.LinkedStack;
import mz.uem.eda.parking.structures.Stack;


/**
 * The sorted stack has the largest element on top, so popping it (or
 * iterating it) gives the elements in descending order of {@code order}.
 */
class TwoStackSortTest {

    private static final Comparator<Integer> ASC = Comparator.naturalOrder();

    private TwoStackSort<Integer> sorter;

    @BeforeEach
    void setUp() {
        sorter = new TwoStackSort<>();
    }

    // ---- trivial inputs ---------------------------------------------------

    @Test
    void emptyInputGivesEmptyStack() {
        Stack<Integer> sorted = sorter.sort(new LinkedStack<>(), ASC);
        assertTrue(sorted.isEmpty());
        assertEquals(0, sorter.getComparisons());
        assertEquals(0, sorter.getReturns());
        assertEquals(0, sorter.getPushes());
    }

    @Test
    void singleElement() {
        Stack<Integer> sorted = sorter.sort(stackOf(7), ASC);
        assertEquals(List.of(7), toList(sorted));
        assertEquals(0, sorter.getComparisons());
        assertEquals(1, sorter.getPushes());
    }

    // ---- ordering ---------------------------------------------------------

    @Test
    void largestEndsOnTop() {
        Stack<Integer> sorted = sorter.sort(stackOf(3, 1, 4, 1, 5, 9, 2, 6), ASC);
        assertEquals(List.of(9, 6, 5, 4, 3, 2, 1, 1), toList(sorted));
    }

    @Test
    void popsInDescendingOrder() {
        Stack<Integer> sorted = sorter.sort(stackOf(20, 5, 15, 10), ASC);
        assertEquals(20, sorted.pop());
        assertEquals(15, sorted.pop());
        assertEquals(10, sorted.pop());
        assertEquals(5, sorted.pop());
        assertTrue(sorted.isEmpty());
    }

    @Test
    void reversedComparatorPutsSmallestOnTop() {
        Stack<Integer> sorted = sorter.sort(stackOf(3, 1, 2), ASC.reversed());
        assertEquals(List.of(1, 2, 3), toList(sorted));
    }

    @Test
    void keepsDuplicates() {
        Stack<Integer> sorted = sorter.sort(stackOf(2, 2, 1, 2), ASC);
        assertEquals(List.of(2, 2, 2, 1), toList(sorted));
    }

    @Test
    void matchesJavaSortOnRandomInput() {
        Random random = new Random(2026);
        for (int round = 0; round < 20; round++) {
            List<Integer> values = new ArrayList<>();
            int n = random.nextInt(100);
            for (int i = 0; i < n; i++) {
                values.add(random.nextInt(500) - 250); // negatives are fine here
            }
            Stack<Integer> input = new LinkedStack<>();
            values.forEach(input::push);

            Stack<Integer> sorted = sorter.sort(input, ASC);

            List<Integer> expected = new ArrayList<>(values);
            expected.sort(ASC.reversed());
            assertEquals(expected, toList(sorted));
        }
    }

    @Test
    void sortsByComparatorOnField() {
        Stack<Car> input = new LinkedStack<>();
        input.push(new Car("A", 17));
        input.push(new Car("B", 9));
        input.push(new Car("C", 12));

        Stack<Car> sorted = new TwoStackSort<Car>().sort(input, Comparator.comparingInt(Car::leavesAt));

        assertEquals("A", sorted.pop().plate());
        assertEquals("C", sorted.pop().plate());
        assertEquals("B", sorted.pop().plate());
    }

    // ---- effect on the input ----------------------------------------------

    @Test
    void inputIsEmptied() {
        Stack<Integer> input = stackOf(3, 1, 2);
        sorter.sort(input, ASC);
        assertTrue(input.isEmpty());
    }

    @Test
    void acceptsBoundedInputStack() {
        // elements go back to the input during sorting; never more than it held
        Stack<Integer> input = new ArrayStack<>(4);
        for (int v : new int[] {1, 2, 3, 4}) {
            input.push(v);
        }
        Stack<Integer> sorted = sorter.sort(input, ASC);
        assertEquals(List.of(4, 3, 2, 1), toList(sorted));
    }

    // ---- counters ---------------------------------------------------------

    @Test
    void bestCaseHasNoReturns() {
        // top of the input is the smallest: each element goes straight on top
        Stack<Integer> input = stackOf(5, 4, 3, 2, 1);
        sorter.sort(input, ASC);
        assertEquals(0, sorter.getReturns());
        assertEquals(5, sorter.getPushes());
        assertEquals(4, sorter.getComparisons());
    }

    @Test
    void worstCaseHasReturns() {
        // top of the input is the largest: every new element sends others back
        Stack<Integer> input = stackOf(1, 2, 3, 4, 5);
        sorter.sort(input, ASC);
        assertTrue(sorter.getReturns() > 0);
        assertTrue(sorter.getComparisons() > 4);
    }

    @Test
    void pushesEqualElementsPlusTwiceTheReturns() {
        // a returned element is pushed back to the input, then onto the
        // sorted stack again when it is popped a second time
        Random random = new Random(7);
        for (int round = 0; round < 10; round++) {
            int n = 1 + random.nextInt(50);
            Stack<Integer> input = new LinkedStack<>();
            for (int i = 0; i < n; i++) {
                input.push(random.nextInt(100));
            }
            sorter.sort(input, ASC);
            assertEquals(n + 2 * sorter.getReturns(), sorter.getPushes());
        }
    }

    @Test
    void countersResetBetweenSorts() {
        sorter.sort(stackOf(1, 2, 3, 4, 5), ASC);
        sorter.sort(stackOf(9), ASC);
        assertEquals(0, sorter.getComparisons());
        assertEquals(0, sorter.getReturns());
        assertEquals(1, sorter.getPushes());
    }

    // ---- null arguments ---------------------------------------------------

    @Test
    void nullInputThrows() {
        assertThrows(IllegalArgumentException.class, () -> sorter.sort(null, ASC));
    }

    @Test
    void nullComparatorThrows() {
        assertThrows(IllegalArgumentException.class, () -> sorter.sort(stackOf(1), null));
    }

    // ---- helpers ----------------------------------------------------------

    private record Car(String plate, int leavesAt) {
    }

    private static Stack<Integer> stackOf(int... bottomToTop) {
        Stack<Integer> stack = new LinkedStack<>();
        for (int v : bottomToTop) {
            stack.push(v);
        }
        return stack;
    }

    private static <T> List<T> toList(Iterable<T> iterable) {
        List<T> list = new ArrayList<>();
        for (T element : iterable) {
            list.add(element);
        }
        return list;
    }
}
