package io.luna.util.parser.impl;

import com.google.common.collect.ImmutableList;
import com.google.gson.JsonObject;
import io.luna.game.model.def.EquipmentPoisonDefinition;
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
        super(Paths.get("data", "game", "def", "equipment", "poison.json"));
    }

    @Override
    public EquipmentPoisonDefinition convert(JsonObject token) {
        int id = token.get("id").getAsInt();
        int severity = token.get("severity").getAsInt();
        return new EquipmentPoisonDefinition(id, severity);
    }

    @Override
    public void onCompleted(ImmutableList<EquipmentPoisonDefinition> tokenObjects) {
        EquipmentPoisonDefinition.ALL.storeAndLock(tokenObjects);
        logger.debug("Loaded {} poison applying items!", box(tokenObjects.size()));
    }
}