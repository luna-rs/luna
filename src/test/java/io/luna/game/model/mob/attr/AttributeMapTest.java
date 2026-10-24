package io.luna.game.model.mob.attr;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for persistent attributes across sessions.
 *
 * @author lare96
 */
final class AttributeMapTest {

    @AfterEach
    void clearKeys() {
        AttributeMap.persistentKeyMap.clear();
    }

    @Test
    void unreadValuesSurviveMultipleSessions() {
        Map<String, Object> saved = Map.of("charges@java.lang.Integer", 1,
                "recharges@java.lang.Integer", 7);
        for (int session = 0; session < 3; session++) {
            AttributeMap map = new AttributeMap();
            map.load(saved);
            assertEquals(saved, map.save());
            assertEquals(2, map.size());
            saved = map.save();
        }
        Attribute<Integer> charges = new Attribute<>(Integer.class, () -> 4).persist("charges");
        AttributeMap map = new AttributeMap();
        map.load(saved);
        assertEquals(1, map.get(charges));
        assertEquals(saved, map.save());
    }

    @Test
    void updatesReplaceUnreadValuesAndKeepOtherValues() {
        Attribute<Integer> charges = new Attribute<>(Integer.class, () -> 4).persist("charges");
        AttributeMap map = new AttributeMap();
        map.load(Map.of("charges@java.lang.Integer", 1, "other@java.lang.Integer", 7));
        map.set(charges, 2);
        assertEquals(Map.of("charges@java.lang.Integer", 2, "other@java.lang.Integer", 7), map.save());
        map.set(charges, null);
        assertEquals("null", map.save().get("charges@java.lang.Integer"));
    }

    @Test
    void preservesDeclaredTypeAndCustomAdapterValues() {
        AttributeMap map = new AttributeMap();
        map.load(Map.of("number@java.lang.Number", 5,
                "time@java.time.Instant", "2026-10-24T12:00:00Z"));
        assertTrue(map.save().containsKey("number@java.lang.Number"));
        assertEquals(Instant.parse("2026-10-24T12:00:00Z"), map.save().get("time@java.time.Instant"));
        AttributeMap reloaded = new AttributeMap();
        reloaded.load(map.save());
        assertEquals(map.save(), reloaded.save());
    }

    @Test
    void loadingAgainInvalidatesCachedValueAndOldPendingValues() {
        Attribute<Integer> charges = new Attribute<>(Integer.class, () -> 4).persist("charges");
        AttributeMap map = new AttributeMap();
        map.load(Map.of("charges@java.lang.Integer", 1, "old@java.lang.Integer", 7));
        assertEquals(1, map.get(charges));
        map.load(Map.of("charges@java.lang.Integer", 2));
        assertEquals(2, map.get(charges));
        assertEquals(Map.of("charges@java.lang.Integer", 2), map.save());
    }

    @Test
    void transientAttributesAreNotSaved() {
        AttributeMap map = new AttributeMap();
        map.set(new Attribute<>(Integer.class, () -> 0), 3);
        assertTrue(map.save().isEmpty());
    }
}
