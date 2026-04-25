package io.luna.util.parser.impl;

import com.google.common.collect.ImmutableList;
import com.google.gson.JsonObject;
import io.luna.game.model.def.EquipmentDefinition;
import io.luna.game.model.def.EquipmentPoisonDefinition;
import io.luna.game.model.def.ItemDefinition;
import io.luna.game.model.item.Equipment;
import io.luna.util.parser.JsonFileParser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Paths;

import static org.apache.logging.log4j.util.Unbox.box;

/**
 * Parses equipment poison definitions from {@code data/game/def/equipment/poison.json}.
 *
 * @author lare96
 */
public final class EquipmentPoisonDefinitionFileParser extends JsonFileParser<EquipmentPoisonDefinition> {

    /**
     * The logger.
     */
    private static final Logger logger = LogManager.getLogger();

    /**
     * Creates a new {@link EquipmentPoisonDefinitionFileParser}.
     */
    public EquipmentPoisonDefinitionFileParser() {
        super(Paths.get("data", "game", "def", "equipment", "weapon_poison.jsonc"));
    }

    @Override
    public EquipmentPoisonDefinition convert(JsonObject token) {
        int id = token.get("id").getAsInt();
        int severity = token.get("severity").getAsInt();
        int index = EquipmentDefinition.ALL.retrieve(id).getIndex();
        if (index != Equipment.WEAPON && index != Equipment.AMMUNITION) {
            String name = ItemDefinition.ALL.retrieve(id).getName();
            throw new IllegalStateException(
                    "(" + id + " | " + name + ") Only ammo and weapons are permitted in weapon_poison.jsonc");
        }
        return new EquipmentPoisonDefinition(id, severity);
    }

    @Override
    public void onCompleted(ImmutableList<EquipmentPoisonDefinition> tokenObjects) {
        EquipmentPoisonDefinition.ALL.storeAndLock(tokenObjects);
        logger.debug("Loaded {} poison applying items!", box(tokenObjects.size()));
    }
}