package io.luna.game.model.mob.attr;

import org.junit.Test;

import java.util.stream.IntStream;

import static org.junit.Assert.assertEquals;

/**
 * A test that ensures that functions within the {@link AttributeKey} and {@link AttributeMap} class are functioning
 * correctly.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class AttributeTest {

    /**
     * Test the {@code String} key aliasing functions.
     */
    @Test
    public void testStringInterning() {
        AttributeMap map = new AttributeMap();

        AttributeKey.forTransient("some_attribute_1", "some_value");

        String key = "some_attribute_2";
        AttributeKey.forTransient(key, 500);

        assertEquals(map.get("some_attribute_1").get(), "some_value");
        assertEquals(map.get("some_attribute_2").get(), 500);

        AttributeKey.ALIASES.clear();
    }

    /**
     * Test the caching functions of the attribute map.
     */
    @Test
    public void testInternalCaching() {
        AttributeMap map = new AttributeMap();

        AttributeKey.forTransient("some_attribute", "some_value");
        IntStream.rangeClosed(0, 5).forEach(it -> assertEquals(map.get("some_attribute").get(), "some_value"));

        AttributeKey.ALIASES.clear();
    }

    /**
     * Test trying to use an attribute map with an non-interned string.
     */
    @Test
    public void testForcedInterning() {
        AttributeMap map = new AttributeMap();

        AttributeKey.forTransient("some_attribute", "some_value");

        //noinspection RedundantStringConstructorCall
        assertEquals(map.get(new String("some_attribute")).get(), "some_value");

        AttributeKey.ALIASES.clear();
    }

    /**
     * Test trying to use a non-existent attribute.
     */
    @Test(expected = IllegalStateException.class)
    public void testNonAliasedAttribute() {
        AttributeMap map = new AttributeMap();

        map.get("some_attribute");
    }

    /**
     * Test aliasing a {@code String} key more than once.
     */
    @Test(expected = IllegalStateException.class)
    public void testMultipleAlias() {
        AttributeKey.forTransient("some_attribute", 0);
        AttributeKey.forTransient("some_attribute", false);

        AttributeKey.ALIASES.clear();
    }

    /**
     * Test aliasing an empty {@code String} key.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testEmptyAlias() {
        AttributeKey.forTransient("", 0);

        AttributeKey.ALIASES.clear();
    }
}
