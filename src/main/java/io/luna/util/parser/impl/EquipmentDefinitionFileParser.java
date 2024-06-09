package io.luna.util.parser.impl;

import com.google.common.collect.ImmutableList;
import com.google.gson.JsonObject;
import io.luna.game.model.def.EquipmentDefinition;
import io.luna.game.model.def.EquipmentDefinition.Requirement;
import io.luna.util.GsonUtils;
import io.luna.util.parser.JsonFileParser;

import java.nio.file.Paths;

/**
 * A {@link JsonFileParser} implementation that reads equipment definitions.
 *
 * @author lare96
 */
public final class EquipmentDefinitionFileParser extends JsonFileParser<EquipmentDefinition> {

    /**
     * Creates a new {@link EquipmentDefinitionFileParser}.
     */
    public EquipmentDefinitionFileParser() {
        super(Paths.get("data", "game", "def", "equipment.json"));
    }

    @Override
    public EquipmentDefinition convert(JsonObject token) {
        int id = token.get("id").getAsInt();
        int index = token.get("index").getAsInt();
        boolean twoHanded = token.get("two_handed?").getAsBoolean();
        boolean fullBody = token.get("full_body?").getAsBoolean();
        boolean fullHelmet = token.get("full_helmet?").getAsBoolean();
        Requirement[] requirements = GsonUtils.getAsType(token.get("requirements"), Requirement[].class);
        int[] bonuses = GsonUtils.getAsType(token.get("bonuses"), int[].class);
        return new EquipmentDefinition(id, index, twoHanded, fullBody, fullHelmet, requirements, bonuses);
    }

    @Override
    public void onCompleted(ImmutableList<EquipmentDefinition> tokenObjects) {
        EquipmentDefinition.ALL.storeAndLock(tokenObjects);
    }
}
