package io.luna.util;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * A test that ensures various functions in the {@link StringUtils} class are functioning correctly.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class StringUtilsTest {

    /**
     * Test the {@code joinWithAnd()} function.
     */
    @Test
    public void testJoinWithAnd() {
        List<String> namesOfFriends = Arrays.asList("Braedan", "Ryley", "Tim", "James");

        // Standard test, normal usage.
        String result = StringUtils.joinWithAnd("The names of my friends are ", ".", namesOfFriends);
        assertEquals("The names of my friends are Braedan, Ryley, Tim, and James.", result);

        // Test with no prefix/suffix.
        result = StringUtils.joinWithAnd("", "", namesOfFriends);
        assertEquals("Braedan, Ryley, Tim, and James", result);

        // Test with no elements.
        result = StringUtils.joinWithAnd("The names of my friends are", ".", new ArrayList<>());
        assertEquals("The names of my friends are.", result);

        // Test with no prefix/suffix + no elements.
        result = StringUtils.joinWithAnd("", "", new ArrayList<>());
        assertTrue(result.isEmpty());
    }
}
