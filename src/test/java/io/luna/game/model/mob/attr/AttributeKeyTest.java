package io.luna.game.model.mob.attr;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for {@link AttributeKey}.
 *
 * @author lare96 <http://github.com/lare96>
 */
final class AttributeKeyTest {

    @AfterEach
    void clearAliases() {
        AttributeKey.ALIASES.clear();
    }

    @Test
    void duplicateKeyName() {
        AttributeKey.forTransient("some_attribute", 0);
        assertThrows(IllegalStateException.class, () -> AttributeKey.forTransient("some_attribute", false));
    }

    @Test
    void emptyKeyName() {
        assertThrows(IllegalArgumentException.class, () -> AttributeKey.forTransient("", 0));
    }
}