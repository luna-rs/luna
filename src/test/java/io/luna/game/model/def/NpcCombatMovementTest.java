package io.luna.game.model.def;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import io.luna.game.model.Position;
import io.luna.util.parser.impl.NpcCombatDefinitionFileParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Lost City leash boundaries and the imported revision 377 movement configuration.
 *
 * @author lare96
 */
class NpcCombatMovementTest {

    @Test
    void meleeIncludesOuterEdgesButExcludesOuterCorners() {
        Position spawn = new Position(100, 100);
        NpcCombatMovement rules = NpcCombatMovement.DEFAULT;
        for (int sx : new int[]{-1, 1}) {
            for (int sy : new int[]{-1, 1}) {
                assertTrue(rules.containsTarget(spawn, spawn.translate(sx * 8, sy * 7)));
                assertFalse(rules.containsTarget(spawn, spawn.translate(sx * 8, sy * 8)));
                assertFalse(rules.containsTarget(spawn, spawn.translate(sx * 9, 0)));
            }
        }
        assertFalse(rules.containsTarget(spawn, new Position(100, 100, 1)));
    }

    @Test
    void rangedBoundsIncludeAttackRangeAndCorners() {
        NpcCombatMovement rules = new NpcCombatMovement(4, 5, -1);
        Position spawn = new Position(100, 100);
        assertTrue(rules.containsTarget(spawn, new Position(109, 109)));
        assertFalse(rules.containsTarget(spawn, new Position(110, 100)));
    }

    @Test
    void importedDefinitionsParseAndKeepDistinctNpcRules() throws Exception {
        Map<Integer, NpcCombatMovement> movement = new HashMap<>();
        NpcCombatDefinitionFileParser parser = new NpcCombatDefinitionFileParser();
        try (var reader = Files.newBufferedReader(Path.of("data/game/def/npcs/npc_combat.jsonc"))) {
            for (JsonElement element : JsonParser.parseReader(reader).getAsJsonArray()) {
                NpcCombatDefinition definition = parser.convert(element.getAsJsonObject());
                assertNull(movement.put(definition.id(), definition.getMovement()));
            }
        }
        assertTrue(movement.size() > 500);
        assertEquals(NpcCombatMovement.DEFAULT, movement.get(1));
        assertEquals(new NpcCombatMovement(7, 0, -1), movement.get(9));
        assertEquals(new NpcCombatMovement(4, 5, -1), movement.get(10));
        assertEquals(new NpcCombatMovement(7, 8, -1), movement.get(13));
        assertEquals(8, movement.get(8).retreatHitpoints());
        assertEquals(25, movement.get(1153).maxRange());
    }

    @Test
    void invalidRangesFailAtLoadTime() {
        assertThrows(IllegalArgumentException.class, () -> new NpcCombatMovement(-1, 0, -1));
        assertThrows(IllegalArgumentException.class, () -> new NpcCombatMovement(7, 256, -1));
        assertThrows(IllegalArgumentException.class, () -> new NpcCombatMovement(7, 0, -2));
    }
}
