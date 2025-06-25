package ru.tehkode.utils;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;

public class StringUtilsTest {

    @Test
    public void testImplodeArray() {
        assertEquals("", StringUtils.implode(new String[]{}, ","));
        assertEquals("a", StringUtils.implode(new String[]{"a"}, ","));
        assertEquals("a,b,c", StringUtils.implode(new String[]{"a", "b", "c"}, ","));
        assertEquals("a b c", StringUtils.implode(new String[]{"a", "b", "c"}, " "));
        assertEquals("a b c", StringUtils.implode(new String[]{" a", "b ", " c "}, " "));
    }

    @Test
    public void testImplodeList() {
        assertEquals("", StringUtils.implode(Collections.emptyList(), ","));
        assertEquals("a", StringUtils.implode(Collections.singletonList("a"), ","));
        assertEquals("a,b,c", StringUtils.implode(Arrays.asList("a", "b", "c"), ","));
        assertEquals("1-2-3", StringUtils.implode(Arrays.asList(1, 2, 3), "-"));
    }

    @Test
    public void testReadStream() throws Exception {
        String testString = "Hello, world!";
        InputStream stream = new ByteArrayInputStream(testString.getBytes("UTF-8"));
        String result = StringUtils.readStream(stream);
        assertEquals(testString, result);

        assertNull(StringUtils.readStream(null));
    }

    @Test
    public void testRepeat() {
        assertEquals("", StringUtils.repeat("abc", 0));
        assertEquals("abc", StringUtils.repeat("abc", 1));
        assertEquals("abcabcabc", StringUtils.repeat("abc", 3));
        assertEquals("", StringUtils.repeat("", 5));
    }

    @Test
    public void testToInteger() {
        assertEquals(123, StringUtils.toInteger("123", 0));
        assertEquals(-5, StringUtils.toInteger("-5", 0));
        assertEquals(0, StringUtils.toInteger("0", 10));
        assertEquals(10, StringUtils.toInteger("notanumber", 10));
        assertEquals(42, StringUtils.toInteger(null, 42));
    }
}
