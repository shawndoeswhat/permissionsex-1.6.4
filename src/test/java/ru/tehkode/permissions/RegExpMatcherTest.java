package ru.tehkode.permissions;
import org.junit.Before;
public class RegExpMatcherTest extends PermissionMatcherTest {

    @Before
    public void setUpMatcher() {
        matcher = new RegExpMatcher();
    }
}
