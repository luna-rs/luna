package io.luna.util.parser.impl;

import com.google.common.collect.ImmutableList;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.luna.game.model.def.EquipmentDefinition;
import io.luna.game.model.def.EquipmentDefinition.Requirement;
import io.luna.util.GsonUtils;
import io.luna.util.parser.GsonParser;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static com.google.common.base.Preconditions.checkState;

/**
 * A {@link GsonParser} implementation that reads equipment definitions.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class EquipmentDefinitionParser extends GsonParser<EquipmentDefinition> {

    @Override
    public EquipmentDefinition readObject(JsonObject reader) throws Exception {
        int id = reader.get("id").getAsInt();
        int index = reader.get("index").getAsInt();
        boolean twoHanded = reader.get("two_handed?").getAsBoolean();
        boolean fullBody = reader.get("full_body?").getAsBoolean();
        boolean fullHelmet = reader.get("full_helmet?").getAsBoolean();
        Set<Requirement> requirements = decodeReqs(reader.get("requirements"));
        int[] bonuses = GsonUtils.getAsType(reader.get("bonuses"), int[].class);

        return new EquipmentDefinition(id, index, twoHanded, fullBody, fullHelmet, requirements, bonuses);
    }

    @Override
    public void onReadComplete(List<EquipmentDefinition> readObjects) throws Exception {
        readObjects.forEach(EquipmentDefinition.ALL::storeDefinition);
        EquipmentDefinition.ALL.lock();
    }

    @Override
    public ImmutableList<String> forFiles() {
        return ImmutableList.of("./data/items/equipment_defs.json");
    }

    /**
     * Reads the requirements from the {@code element}.
     */
    private Set<Requirement> decodeReqs(JsonElement jsonRequirements) {
        Set<Requirement> requirementSet = new LinkedHashSet<>();
        for (JsonElement element : jsonRequirements.getAsJsonArray()) {
            Requirement newValue = new Requirement(element.getAsJsonObject());

            boolean noPreviousValue = requirementSet.add(newValue);
            checkState(noPreviousValue, "Requirement already defined for skill " + newValue + ".");
        }
        return requirementSet;
    }
}
