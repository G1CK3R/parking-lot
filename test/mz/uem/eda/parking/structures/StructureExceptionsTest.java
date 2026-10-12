package mz.uem.eda.parking.structures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import org.junit.jupiter.api.Test;

import mz.uem.eda.parking.structures.exceptions.EmptyStructureException;
import mz.uem.eda.parking.structures.exceptions.FullStructureException;

class StructureExceptionsTest {

    @Test
    void emptyStructureExceptionIsUnchecked() {
        assertInstanceOf(RuntimeException.class, new EmptyStructureException());
    }

    @Test
    void fullStructureExceptionIsUnchecked() {
        assertInstanceOf(RuntimeException.class, new FullStructureException());
    }

    @Test
    void emptyStructureExceptionHasDefaultMessage() {
        assertEquals("structure is empty", new EmptyStructureException().getMessage());
    }

    @Test
    void fullStructureExceptionHasDefaultMessage() {
        assertEquals("structure is full", new FullStructureException().getMessage());
    }

    @Test
    void emptyStructureExceptionKeepsCustomMessage() {
        assertEquals("lane 3 is empty", new EmptyStructureException("lane 3 is empty").getMessage());
    }

    @Test
    void fullStructureExceptionKeepsCustomMessage() {
        assertEquals("lane 3 is full", new FullStructureException("lane 3 is full").getMessage());
    }
}
