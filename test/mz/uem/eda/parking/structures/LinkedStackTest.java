package mz.uem.eda.parking.structures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class LinkedStackTest extends StackContractTest {

    @Override
    Stack<String> newStack() {
        return new LinkedStack<>();
    }

    @Test
    void isNeverFull() {
        Stack<Integer> stack = new LinkedStack<>();
        for (int i = 0; i < 10_000; i++) {
            stack.push(i);
            assertFalse(stack.isFull());
        }
        assertEquals(10_000, stack.size());
        assertEquals(9_999, stack.peek());
    }
}
