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

class ArrayStackTest extends StackContractTest {

    @Override
    Stack<String> newStack() {
        return new ArrayStack<>(CAPACITY);
    }

    // ---- construction -----------------------------------------------------

    @ParameterizedTest
    @ValueSource(ints = {0, -1, Integer.MIN_VALUE})
    void rejectsCapacityBelowOne(int capacity) {
        assertThrows(IllegalArgumentException.class, () -> new ArrayStack<String>(capacity));
    }

    @Test
    void capacityOneIsValid() {
        Stack<String> stack = new ArrayStack<>(1);
        assertTrue(stack.isEmpty());
        stack.push("A");
        assertTrue(stack.isFull());
        assertEquals("A", stack.pop());
        assertTrue(stack.isEmpty());
    }

    // ---- full stack -------------------------------------------------------

    @Test
    void becomesFullAtCapacity() {
        Stack<String> stack = new ArrayStack<>(3);
        stack.push("A");
        stack.push("B");
        assertFalse(stack.isFull());
        stack.push("C");
        assertTrue(stack.isFull());
        assertEquals(3, stack.size());
    }

    @Test
    void pushOnFullThrowsAndLeavesStackUnchanged() {
        Stack<String> stack = new ArrayStack<>(2);
        stack.push("A");
        stack.push("B");
        assertThrows(FullStructureException.class, () -> stack.push("C"));
        assertEquals(2, stack.size());
        assertEquals("B", stack.peek());
        assertEquals(List.of("B", "A"), toList(stack));
    }

    @Test
    void popFromFullFreesOneSlot() {
        Stack<String> stack = new ArrayStack<>(2);
        stack.push("A");
        stack.push("B");
        stack.pop();
        assertFalse(stack.isFull());
        stack.push("C");
        assertTrue(stack.isFull());
        assertEquals("C", stack.pop());
        assertEquals("A", stack.pop());
    }

    @Test
    void fullStackPopsInLifoOrder() {
        Stack<Integer> stack = new ArrayStack<>(5);
        for (int i = 0; i < 5; i++) {
            stack.push(i);
        }
        for (int i = 4; i >= 0; i--) {
            assertEquals(i, stack.pop());
        }
        assertTrue(stack.isEmpty());
    }

    @Test
    void fullStackIteratesTopToBottom() {
        Stack<Integer> stack = new ArrayStack<>(4);
        for (int i = 0; i < 4; i++) {
            stack.push(i);
        }
        assertEquals(List.of(3, 2, 1, 0), toList(stack));
    }

    @Test
    void nullOnFullStackThrowsNullPointer() {
        Stack<String> stack = new ArrayStack<>(1);
        stack.push("A");
        assertThrows(NullPointerException.class, () -> stack.push(null));
    }
}
