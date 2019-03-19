package io.luna.game.model.mob.attr;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link Attribute}.
 *
 * @author lare96 <http://github.com/lare96>
 */
final class AttributeTest {

    @AfterEach
    void clearKeySet() {
        AttributeMap.persistentKeySet.clear();
    }

    @Test
    void illegalKeys() {

        // Test for duplicate keys.
        new Attribute<>(false).persist("duplicate_key");
        assertThrows(IllegalStateException.class, () -> new Attribute<>(false).persist("duplicate_key"));

        // Test for empty key.
        assertThrows(IllegalArgumentException.class, () -> new Attribute<>(false).persist(""));

        // Test for whitespace characters.
        assertThrows(IllegalArgumentException.class, () -> new Attribute<>(false).persist("test key"));

        // Test for uppercase characters.
        assertThrows(IllegalArgumentException.class, () -> new Attribute<>(false).persist("TEST_KEY"));
    }

    @Test
    void defaultSerialization() {
        var attr = new Attribute<>(false).persist("test_key");

        // Test serializing the attribute.
        var value = attr.write(true);
        assertTrue(value.getAsBoolean());

        // Test deserializing the attribute.
        assertTrue(attr.read(value));
    }
}