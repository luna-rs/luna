package io.luna.game.model.mob.attr;

import io.luna.util.GsonUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for {@link Attribute}.
 *
 * @author lare96 
 */
final class AttributeTest {

    @AfterEach
    void clearKeySet() {
        AttributeMap.persistentKeyMap.clear();
    }

    @Test
    void illegalKeys() {
        var gson = GsonUtils.GSON;

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
}