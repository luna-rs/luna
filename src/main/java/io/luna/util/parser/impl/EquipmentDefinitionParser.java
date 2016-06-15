package io.luna.util.parser.impl;

import com.google.common.collect.Iterables;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.luna.game.model.def.EquipmentDefinition;
import io.luna.game.model.def.EquipmentDefinition.EquipmentRequirement;
import io.luna.util.GsonUtils;
import io.luna.util.parser.GsonParser;

import java.util.ArrayList;
import java.util.List;

/**
 * A {@link GsonParser} implementation that reads {@link EquipmentDefinition}s.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class EquipmentDefinitionParser extends GsonParser<EquipmentDefinition> {

    /**
     * Creates a new {@link EquipmentDefinitionParser}.
     */
    public EquipmentDefinitionParser() {
        super("./data/items/equipment_defs.json");
    }

    @Override
    public EquipmentDefinition readObject(JsonObject reader) throws Exception {
        int id = reader.get("id").getAsInt();
        int index = reader.get("index").getAsInt();
        boolean twoHanded = reader.get("two_handed").getAsBoolean();
        boolean fullBody = reader.get("full_body").getAsBoolean();
        boolean fullHelmet = reader.get("full_helmet").getAsBoolean();
        EquipmentRequirement[] requirements = decodeReqs(reader.get("decodeReqs"));
        int[] bonuses = GsonUtils.getAsType(reader.get("bonuses"), int[].class);

        return new EquipmentDefinition(id, index, twoHanded, fullBody, fullHelmet, requirements, bonuses);
    }

    @Override
    public void onReadComplete(List<EquipmentDefinition> readObjects) throws Exception {
        readObjects.forEach(it -> EquipmentDefinition.DEFINITIONS.put(it.getId(), it));
    }

    /**
     * Reads the requirements from the {@code element}.
     */
    private EquipmentRequirement[] decodeReqs(JsonElement element) {
        List<EquipmentRequirement> requirements = new ArrayList<>();

        for (JsonElement nextElement : element.getAsJsonArray()) {
            JsonObject requirement = nextElement.getAsJsonObject();

            String name = requirement.get("name").getAsString();
            int level = requirement.get("level").getAsInt();

            requirements.add(new EquipmentRequirement(name, level));
        }
        return Iterables.toArray(requirements, EquipmentRequirement.class);
    }
}
