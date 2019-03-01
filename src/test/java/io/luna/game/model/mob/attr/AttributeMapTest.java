package io.luna.game.model.mob.attr;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for {@link AttributeMap}.
 *
 * @author lare96 <http://github.com/lare96>
 */
final class AttributeMapTest {

    AttributeMap map;

    @BeforeEach
    void initMap() {
        map = new AttributeMap();
    }

    @AfterEach
    void clearAliases() {
        AttributeKey.ALIASES.clear();
    }

    @Test
    void defaultValues() {
        AttributeKey.forTransient("attribute_1", "value");
        AttributeKey.forTransient("attribute_2", 0);

        assertEquals(map.get("attribute_1").get(), "value");
        assertEquals(map.get("attribute_2").get(), 0);
    }

    /**
     * @noinspection RedundantStringConstructorCall
     */
    @Test
    void forcedInterning() {
        AttributeKey.forTransient("attribute_1", "value");
        AttributeKey.forTransient("attribute_2", "value");

        assertEquals(map.get(new String("attribute_1")).get(), "value");
        assertEquals(map.get(new String("attribute_2")).get(), "value");
    }

    @Test
    void unaliasedAttribute() {
        assertThrows(IllegalStateException.class, () -> map.get("some_attribute"));
    }
}
