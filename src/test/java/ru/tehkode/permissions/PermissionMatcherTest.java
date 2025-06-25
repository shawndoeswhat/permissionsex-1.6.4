package ru.tehkode.permissions;

import org.junit.Test;
import static org.junit.Assert.*;

public abstract class PermissionMatcherTest {

    protected PermissionMatcher matcher;

    @Test
    public void matchesShouldBeCaseInsensitive() {
        assertTrue(matcher.isMatches("PERMISSION.*", "permission.Case.Are.does.NOT.matter"));
    }

    @Test
    public void shouldMatchWithinNumericRange() {
        assertTrue(matcher.isMatches("permission.range.(100-200)", "permission.range.100"));
        assertTrue(matcher.isMatches("permission.range.(100-200)", "permission.range.150"));
        assertTrue(matcher.isMatches("permission.range.(100-200)", "permission.range.200"));
    }

    @Test
    public void shouldNotMatchOutsideNumericRange() {
        assertFalse(matcher.isMatches("permission.range.(100-200)", "permission.range.99"));
        assertFalse(matcher.isMatches("permission.range.(100-200)", "permission.range.201"));
    }

    @Test
    public void shouldHandleSpecialPrefixPermissions() {
        assertTrue(matcher.isMatches("-permission.*", "permission.anything"));
        assertTrue(matcher.isMatches("#permission.*", "permission.anything"));
        assertTrue(matcher.isMatches("-#permission.*", "permission.anything"));
    }

    @Test
    public void shouldNotMatchSpecialPrefixedWhenNotSpecified() {
        assertFalse(matcher.isMatches("permission.*", "#permission.anything"));
    }
}
