package io.luna.game.model.mob.attr;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for {@link AttributeMap}.
 *
 * @author lare96 <http://github.com/lare96>
 */
final class AttributeMapTest {

    AttributeMap map;
    Attribute<Integer> attr;

    @BeforeEach
    void init() {
        attr = new Attribute<>(0).persist("test_key");
        map = new AttributeMap();
        map.load("test_key", 5);
    }

    @AfterEach
    void cleanUp() {
        AttributeMap.persistentKeySet.clear();
    }

    @Test
    void setValue() {
        map.set(attr, 10);
        assertEquals(10, map.get(attr));
    }

    @Test
    void getValue() {
        assertEquals(5, map.get(attr));
    }
}
