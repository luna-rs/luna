package io.luna.util.parser.impl;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import io.luna.game.model.def.WeaponAnimationDefinition;
import io.luna.game.model.mob.combat.CombatStyle;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static io.luna.game.model.mob.combat.CombatStyle.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Checks weapon animation overrides and the production equipment data.
 *
 * @author lare96
 */
final class WeaponAnimationDefinitionFileParserTest {

    @Test
    void combatOnlyOverrideRetainsModelAndUnspecifiedAnimationFallbacks() {
        var token = JsonParser.parseString("""
                {"ids":[837],"styles":[{"type":"CROSSBOW_ACCURATE","attack":-1,"defend":424}]}
                """).getAsJsonObject();
        var definition = new WeaponAnimationDefinitionFileParser().convert(token);
        assertNull(definition.getModel());
        assertEquals(-1, definition.getAttackAnimation(CROSSBOW_ACCURATE));
        assertEquals(424, definition.getDefenceAnimation(CROSSBOW_ACCURATE));
        assertEquals(-1, definition.getAttackAnimation(CROSSBOW_RAPID));
        assertEquals(-1, definition.getDefenceAnimation(CROSSBOW_RAPID));
    }

    @Test
    void productionOverridesHaveUniqueItemsAndValidWeaponStyles() throws IOException {
        var definitions = readDefinitions();
        Map<Integer, String> weaponTypes = new HashMap<>();
        Map<String, Set<CombatStyle>> styles = new HashMap<>();
        try (var reader = Files.newBufferedReader(equipmentPath("weapon_types.jsonc"))) {
            for (JsonElement element : JsonParser.parseReader(reader).getAsJsonArray()) {
                var token = element.getAsJsonObject();
                weaponTypes.put(token.get("id").getAsInt(), token.get("type").getAsString());
            }
        }
        try (var reader = Files.newBufferedReader(equipmentPath("weapon_type_data.jsonc"))) {
            for (JsonElement element : JsonParser.parseReader(reader).getAsJsonArray()) {
                var token = element.getAsJsonObject();
                Set<CombatStyle> supported = new HashSet<>();
                for (JsonElement style : token.getAsJsonArray("styles")) {
                    supported.add(CombatStyle.valueOf(style.getAsJsonObject().get("type").getAsString()));
                    if (style.getAsJsonObject().get("type").getAsString().equals("DAGGER_SLASH")) {
                        assertEquals("SLASH_ATTACK", style.getAsJsonObject().get("bonus").getAsString());
                    }
                }
                styles.put(token.get("type").getAsString(), supported);
            }
        }
        definitions.forEach((id, definition) -> {
            assertTrue(weaponTypes.containsKey(id), "Unknown weapon " + id);
            assertTrue(styles.get(weaponTypes.get(id)).containsAll(definition.getStyles().keySet()),
                    "Unsupported style for weapon " + id);
        });
    }

    @Test
    void lostCityAnimationRegressions() throws IOException {
        var definitions = readDefinitions();
        for (int id : new int[]{1215, 1231, 5680, 5698}) {
            assertEquals(376, definitions.get(id).getAttackAnimation(DAGGER_STAB));
            assertEquals(377, definitions.get(id).getAttackAnimation(DAGGER_SLASH));
            assertEquals(378, definitions.get(id).getDefenceAnimation(DAGGER_BLOCK));
        }
        assertEquals(1665, definitions.get(4153).getAttackAnimation(WARHAMMER_POUND));
        assertEquals(1666, definitions.get(4153).getDefenceAnimation(WARHAMMER_BLOCK));
        assertEquals(1067, definitions.get(3095).getAttackAnimation(CLAWS_LUNGE));
        assertEquals(393, definitions.get(3095).getAttackAnimation(CLAWS_SLASH));
        assertEquals(401, definitions.get(1265).getAttackAnimation(PICKAXE_SPIKE));
        assertEquals(400, definitions.get(1265).getAttackAnimation(PICKAXE_SMASH));
        assertEquals(439, definitions.get(1419).getAttackAnimation(SCYTHE_BLOCK));
        assertEquals(1428, definitions.get(4037).getAttackAnimation(STAFF_BASH));
        assertEquals(1429, definitions.get(4037).getDefenceAnimation(STAFF_BASH));
        assertEquals(1421, definitions.get(4037).getModel().getStanding());
        assertEquals(1422, definitions.get(4037).getModel().getWalking());
        assertEquals(1427, definitions.get(4037).getModel().getRunning());
        assertEquals(809, definitions.get(1305).getModel().getStanding());
        assertEquals(-1, definitions.get(1305).getAttackAnimation(LONGSWORD_CHOP));
    }

    private Map<Integer, WeaponAnimationDefinition> readDefinitions() throws IOException {
        var parser = new WeaponAnimationDefinitionFileParser();
        Map<Integer, WeaponAnimationDefinition> definitions = new HashMap<>();
        try (var reader = Files.newBufferedReader(equipmentPath("weapon_animations.jsonc"))) {
            for (JsonElement element : parser.newParser(reader)) {
                var definition = parser.convert(element.getAsJsonObject());
                for (int id : definition.getIds()) {
                    assertNull(definitions.put(id, definition), "Duplicate override for " + id);
                }
            }
        }
        return definitions;
    }

    private Path equipmentPath(String file) {
        return Path.of("data", "game", "def", "equipment", file);
    }
}
