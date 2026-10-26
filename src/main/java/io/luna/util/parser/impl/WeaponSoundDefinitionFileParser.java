package io.luna.util.parser.impl;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import game.player.Sound;
import io.luna.game.model.def.WeaponSoundDefinition;
import io.luna.game.model.item.Equipment.EquipmentBonus;
import io.luna.util.parser.JsonFileParser;

import java.nio.file.Path;
import java.util.EnumMap;

/**
 * Loads grouped ordinary weapon sounds from {@code weapon_sounds.jsonc}.
 * <p>
 * Each entry contains item {@code ids} and a {@code sounds} object keyed by offensive bonus names. Values name
 * existing sound constants. Empty mappings explicitly retain silence. Unknown constants, unsupported bonus types,
 * empty ID groups, and duplicate item IDs reject the load.
 *
 * @author lare96
 */
public final class WeaponSoundDefinitionFileParser extends JsonFileParser<WeaponSoundDefinition> {

    /**
     * Creates the parser for the equipment sound definitions.
     */
    public WeaponSoundDefinitionFileParser() {
        super(Path.of("data", "game", "def", "equipment", "weapon_sounds.jsonc"));
    }

    @Override
    public WeaponSoundDefinition convert(JsonObject token) {
        ImmutableSet.Builder<Integer> ids = ImmutableSet.builder();
        for (JsonElement id : token.getAsJsonArray("ids")) {
            ids.add(id.getAsInt());
        }
        ImmutableSet<Integer> itemIds = ids.build();
        if (itemIds.isEmpty()) {
            throw new IllegalArgumentException("A weapon sound definition must contain item IDs.");
        }
        EnumMap<EquipmentBonus, Sound> sounds = new EnumMap<>(EquipmentBonus.class);
        for (var entry : token.getAsJsonObject("sounds").entrySet()) {
            EquipmentBonus bonus = EquipmentBonus.valueOf(entry.getKey());
            switch (bonus) {
                case STAB_ATTACK, SLASH_ATTACK, CRUSH_ATTACK, RANGED_ATTACK ->
                        sounds.put(bonus, Sound.valueOf(entry.getValue().getAsString()));
                default -> throw new IllegalArgumentException("Invalid weapon sound bonus: " + bonus);
            }
        }
        return new WeaponSoundDefinition(itemIds, sounds);
    }

    @Override
    public void onCompleted(ImmutableList<WeaponSoundDefinition> tokenObjects) {
        WeaponSoundDefinition.load(tokenObjects);
    }
}
