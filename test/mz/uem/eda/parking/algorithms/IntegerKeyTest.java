package mz.uem.eda.parking.algorithms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class IntegerKeyTest {

    private final IntegerKey key = new IntegerKey();

    @Test
    void keyIsTheValueItself() {
        assertEquals(0, key.of(0));
        assertEquals(42, key.of(42));
        assertEquals(-7, key.of(-7));
        assertEquals(Integer.MAX_VALUE, key.of(Integer.MAX_VALUE));
    }

    @Test
    void nullThrows() {
        assertThrows(NullPointerException.class, () -> key.of(null));
    }
}
