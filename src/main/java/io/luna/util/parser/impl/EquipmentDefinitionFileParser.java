package io.luna.util.parser.impl;

import com.google.common.collect.Iterables;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.luna.game.model.def.EquipmentDefinition;
import io.luna.game.model.def.EquipmentDefinition.Requirement;
import io.luna.util.GsonUtils;
import io.luna.util.parser.JsonFileParser;

import java.util.ArrayList;
import java.util.List;

/**
 * A {@link JsonFileParser} implementation that reads equipment definitions.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class EquipmentDefinitionFileParser extends JsonFileParser<EquipmentDefinition> {

    /**
     * Creates a new {@link EquipmentDefinitionFileParser}.
     */
    public EquipmentDefinitionFileParser() {
        super("./data/def/items/equipment_defs.json");
    }

    @Override
    public EquipmentDefinition convert(JsonObject token) throws Exception {
        int id = token.get("id").getAsInt();
        int index = token.get("index").getAsInt();
        boolean twoHanded = token.get("two_handed?").getAsBoolean();
        boolean fullBody = token.get("full_body?").getAsBoolean();
        boolean fullHelmet = token.get("full_helmet?").getAsBoolean();
        Requirement[] requirements = jsonReqToArray(token.get("requirements").getAsJsonArray());
        int[] bonuses = GsonUtils.getAsType(token.get("bonuses"), int[].class);
        return new EquipmentDefinition(id, index, twoHanded, fullBody, fullHelmet, requirements, bonuses);
    }

    @Override
    public void onCompleted(List<EquipmentDefinition> tokenObjects) throws Exception {
        EquipmentDefinition.ALL.storeAndLock(tokenObjects);
    }

    /**
     * Converts a {@link JsonArray} of requirements to a Java array.
     *
     * @param requirements The requirement data, in JSON.
     * @return The requirement data in a Java array.a
     */
    private Requirement[] jsonReqToArray(JsonArray requirements) {
        List<Requirement> reqList = new ArrayList<>(requirements.size());
        for(JsonElement jsonReq : requirements) {
            reqList.add(new Requirement(jsonReq.getAsJsonObject()));
        }
        return Iterables.toArray(reqList, Requirement.class);
    }
}
