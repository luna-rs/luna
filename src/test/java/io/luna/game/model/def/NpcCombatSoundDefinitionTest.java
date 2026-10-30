package io.luna.game.model.def;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.luna.util.parser.impl.NpcCombatDefinitionFileParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Checks optional sound fields and representative Lost City revision 377 NPC sound mappings.
 *
 * @author lare96
 */
class NpcCombatSoundDefinitionTest {

    private static JsonObject template() throws Exception {
        try (var reader = Files.newBufferedReader(Path.of("data/game/def/npcs/npc_combat.jsonc"))) {
            JsonObject token = JsonParser.parseReader(reader).getAsJsonArray().get(0).getAsJsonObject();
            token.remove("attack_sound");
            token.remove("ranged_attack_sound");
            token.remove("death_sound");
            return token;
        }
    }

    @Test
    void absentSoundFieldsKeepOlderDefinitionsSilent() throws Exception {
        NpcCombatDefinition definition = new NpcCombatDefinitionFileParser().convert(template());
        assertEquals(-1, definition.getAttackSound());
        assertEquals(-1, definition.getRangedAttackSound());
        assertEquals(-1, definition.getDeathSound());
    }

    @Test
    void soundZeroAndExplicitSilenceAreBothPreserved() throws Exception {
        JsonObject token = template();
        token.addProperty("attack_sound", 0);
        token.addProperty("ranged_attack_sound", -1);
        token.addProperty("death_sound", 0);
        NpcCombatDefinition definition = new NpcCombatDefinitionFileParser().convert(token);
        assertEquals(0, definition.getAttackSound());
        assertEquals(-1, definition.getRangedAttackSound());
        assertEquals(0, definition.getDeathSound());
    }

    @Test
    void invalidSoundIdsFailBeforeTheyReachThePacketWriter() throws Exception {
        for (String field : new String[]{"attack_sound", "ranged_attack_sound", "death_sound"}) {
            for (int invalid : new int[]{-2, 65536}) {
                JsonObject token = template();
                token.addProperty(field, invalid);
                assertThrows(IllegalArgumentException.class,
                        () -> new NpcCombatDefinitionFileParser().convert(token), field + "=" + invalid);
            }
        }
    }

    @Test
    void importedSoundsKeepDistinctNpcFamiliesAndExplicitNulls() throws Exception {
        Map<Integer, NpcCombatDefinition> definitions = new HashMap<>();
        NpcCombatDefinitionFileParser parser = new NpcCombatDefinitionFileParser();
        try (var reader = Files.newBufferedReader(Path.of("data/game/def/npcs/npc_combat.jsonc"))) {
            for (JsonElement element : JsonParser.parseReader(reader).getAsJsonArray()) {
                NpcCombatDefinition definition = parser.convert(element.getAsJsonObject());
                assertNull(definitions.put(definition.id(), definition));
            }
        }
        assertTrue(definitions.size() > 1000);
        assertSounds(definitions, 78, 1, -1, 0);
        assertSounds(definitions, 81, 4, -1, 3);
        assertSounds(definitions, 53, 116, -1, 114);
        assertSounds(definitions, 1, 417, -1, 70);
        assertSounds(definitions, 4, 417, -1, 71);
        assertSounds(definitions, 27, 417, 370, 70);
        assertSounds(definitions, 391, -1, -1, -1);
        assertSounds(definitions, 1158, 547, -1, -1);
        assertSounds(definitions, 3340, 2404, -1, 2402);
        assertSounds(definitions, 2025, 1070, -1, 1072);
        assertSounds(definitions, 2028, 1075, 1075, 1072);
        assertSounds(definitions, 2739, 1821, 1819, 1822);
        assertSounds(definitions, 3808, 2481, -1, 2480);
        assertSounds(definitions, 1183, 6, 6, 7);
        assertSounds(definitions, 1456, 138, 138, 140);
    }

    private static void assertSounds(Map<Integer, NpcCombatDefinition> definitions, int id,
                                     int attack, int ranged, int death) {
        NpcCombatDefinition definition = definitions.get(id);
        assertNotNull(definition, "NPC " + id);
        assertAll("NPC " + id,
                () -> assertEquals(attack, definition.getAttackSound()),
                () -> assertEquals(ranged, definition.getRangedAttackSound()),
                () -> assertEquals(death, definition.getDeathSound()));
    }
}
