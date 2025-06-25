package ru.tehkode.utils;

import org.junit.Test;
import static org.junit.Assert.*;

public class FieldReplacerTest {

    @SuppressWarnings("unused")
    private static class Dummy {
        private String secret = "initial";
        private int number = 42;
    }

    @Test
    public void testGetAndSetPrivateStringField() {
        FieldReplacer<Dummy, String> replacer = new FieldReplacer<>(Dummy.class, "secret", String.class);
        Dummy dummy = new Dummy();

        assertEquals("initial", replacer.get(dummy));

        replacer.set(dummy, "changed");
        assertEquals("changed", replacer.get(dummy));

        replacer.set(dummy, null);
        assertNull(replacer.get(dummy));
    }

    @Test
    public void testGetAndSetPrivateIntField() {
        FieldReplacer<Dummy, Integer> replacer = new FieldReplacer<>(Dummy.class, "number", Integer.class);
        Dummy dummy = new Dummy();

        assertEquals(Integer.valueOf(42), replacer.get(dummy));

        replacer.set(dummy, 99);
        assertEquals(Integer.valueOf(99), replacer.get(dummy));

        replacer.set(dummy, 0);
        assertEquals(Integer.valueOf(0), replacer.get(dummy));

        replacer.set(dummy, -1);
        assertEquals(Integer.valueOf(-1), replacer.get(dummy));
    }

    @Test(expected = ExceptionInInitializerError.class)
    public void testFieldNotFound() {
        new FieldReplacer<>(Dummy.class, "nonExistentField", String.class);
    }

    @Test(expected = ExceptionInInitializerError.class)
    public void testFieldWrongType() {
        new FieldReplacer<>(Dummy.class, "number", String.class);
    }
}
