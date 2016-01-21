package io.luna.game.model.mobile.attr;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * A test that ensures that functions within the {@link AttributeKey} and {@link AttributeMap} class are functioning
 * correctly.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class AttributeTest {

    /**
     * Test the {@link String} key aliasing functions.
     */
    @Test
    public void testStringInterning() {
        Thread.currentThread().setName("LunaInitializationThread");

        AttributeMap map = new AttributeMap();

        AttributeKey.forTransient("some_attribute_1", "someValue");

        String key = "some_attribute_2";
        AttributeKey.forTransient(key, 500);

        assertEquals(map.get("some_attribute_1").get(), "someValue");
        assertEquals(map.get("some_attribute_2").get(), 500);
    }

    /**
     * Test aliasing a {@link String} key more than once.
     */
    @Test(expected = IllegalStateException.class)
    public void testMultipleAlias() {
        Thread.currentThread().setName("LunaInitializationThread");

        AttributeKey.forTransient("some_attribute", 0);
        AttributeKey.forTransient("some_attribute", false);
    }

    /**
     * Test aliasing an empty {@link String} key.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testEmptyAlias() {
        Thread.currentThread().setName("LunaInitializationThread");

        AttributeKey.forTransient("", 0);
    }

    /**
     * Test creation of key alias from anywhere but the initialization thread.
     */
    @Test(expected = IllegalStateException.class)
    public void testRandomThreadAlias() {
        // Thread.currentThread().setName("LunaInitializationThread");

        AttributeKey.forTransient("some_attribute", 0);
    }
}
