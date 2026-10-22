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
 * Contract shared by every {@link Stack}: LIFO order, empty-stack errors,
 * null rejection and top-to-bottom iteration. Subclasses supply the
 * implementation and test what is specific to it.
 */
abstract class StackContractTest {

    /** Capacity large enough for every test in this class. */
    static final int CAPACITY = 16;

    private Stack<String> stack;

    /** @return a new, empty stack holding at least {@link #CAPACITY} elements */
    abstract Stack<String> newStack();

    @BeforeEach
    void setUp() {
        stack = newStack();
    }

    // ---- empty stack ------------------------------------------------------

    @Test
    void newStackIsEmpty() {
        assertTrue(stack.isEmpty());
        assertEquals(0, stack.size());
        assertFalse(stack.isFull());
    }

    @Test
    void popOnEmptyThrows() {
        assertThrows(EmptyStructureException.class, stack::pop);
    }

    @Test
    void peekOnEmptyThrows() {
        assertThrows(EmptyStructureException.class, stack::peek);
    }

    @Test
    void popAfterDrainingThrows() {
        stack.push("A");
        stack.pop();
        assertThrows(EmptyStructureException.class, stack::pop);
        assertThrows(EmptyStructureException.class, stack::peek);
    }

    @Test
    void iteratorOnEmptyHasNothing() {
        Iterator<String> it = stack.iterator();
        assertFalse(it.hasNext());
        assertThrows(NoSuchElementException.class, it::next);
    }

    // ---- null -------------------------------------------------------------

    @Test
    void pushNullThrowsAndLeavesStackUnchanged() {
        stack.push("A");
        assertThrows(NullPointerException.class, () -> stack.push(null));
        assertEquals(1, stack.size());
        assertEquals("A", stack.peek());
    }

    // ---- push / peek / pop ------------------------------------------------

    @Test
    void pushIncrementsSize() {
        stack.push("A");
        assertFalse(stack.isEmpty());
        assertEquals(1, stack.size());
        stack.push("B");
        assertEquals(2, stack.size());
    }

    @Test
    void peekReturnsTopWithoutRemoving() {
        stack.push("A");
        stack.push("B");
        assertEquals("B", stack.peek());
        assertEquals("B", stack.peek());
        assertEquals(2, stack.size());
    }

    @Test
    void popReturnsSameInstancePushed() {
        String element = new String("A");
        stack.push(element);
        assertSame(element, stack.pop());
    }

    @Test
    void popFollowsLifoOrder() {
        stack.push("A");
        stack.push("B");
        stack.push("C");
        assertEquals("C", stack.pop());
        assertEquals("B", stack.pop());
        assertEquals("A", stack.pop());
        assertTrue(stack.isEmpty());
        assertEquals(0, stack.size());
    }

    @Test
    void interleavedPushAndPopKeepLifoOrder() {
        stack.push("A");
        stack.push("B");
        assertEquals("B", stack.pop());
        stack.push("C");
        stack.push("D");
        assertEquals("D", stack.pop());
        assertEquals("C", stack.pop());
        assertEquals("A", stack.pop());
        assertTrue(stack.isEmpty());
    }

    @Test
    void acceptsDuplicates() {
        stack.push("A");
        stack.push("A");
        assertEquals(2, stack.size());
        assertEquals("A", stack.pop());
        assertEquals("A", stack.pop());
    }

    @Test
    void canBeReusedAfterEmptied() {
        for (int round = 0; round < 3; round++) {
            for (int i = 0; i < CAPACITY; i++) {
                stack.push("E" + i);
            }
            for (int i = CAPACITY - 1; i >= 0; i--) {
                assertEquals("E" + i, stack.pop());
            }
            assertTrue(stack.isEmpty());
        }
    }

    // ---- iteration --------------------------------------------------------

    @Test
    void iteratesFromTopToBottom() {
        stack.push("A");
        stack.push("B");
        stack.push("C");
        assertEquals(List.of("C", "B", "A"), toList(stack));
    }

    @Test
    void iterationDoesNotModifyStack() {
        stack.push("A");
        stack.push("B");
        toList(stack);
        assertEquals(2, stack.size());
        assertEquals("B", stack.peek());
    }

    @Test
    void iteratorMatchesPopOrder() {
        for (int i = 0; i < 5; i++) {
            stack.push("E" + i);
        }
        List<String> iterated = toList(stack);
        List<String> popped = new ArrayList<>();
        while (!stack.isEmpty()) {
            popped.add(stack.pop());
        }
        assertEquals(popped, iterated);
    }

    @Test
    void iteratorThrowsWhenExhausted() {
        stack.push("A");
        Iterator<String> it = stack.iterator();
        assertEquals("A", it.next());
        assertFalse(it.hasNext());
        assertThrows(NoSuchElementException.class, it::next);
    }

    @Test
    void iteratorReflectsStateAfterPop() {
        stack.push("A");
        stack.push("B");
        stack.push("C");
        stack.pop();
        assertEquals(List.of("B", "A"), toList(stack));
    }

    static <T> List<T> toList(Iterable<T> iterable) {
        List<T> list = new ArrayList<>();
        for (T element : iterable) {
            list.add(element);
        }
        return list;
    }
}
