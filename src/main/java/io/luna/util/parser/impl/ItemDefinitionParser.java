package io.luna.util.parser.impl;

import com.google.gson.JsonObject;
import io.luna.game.model.def.ItemDefinition;
import io.luna.util.GsonUtils;
import io.luna.util.parser.GsonParser;

import java.util.List;

/**
 * A {@link GsonParser} implementation that reads {@link ItemDefinition}s.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class ItemDefinitionParser extends GsonParser<ItemDefinition> {

    /**
     * Creates a new {@link ItemDefinitionParser}.
     */
    public ItemDefinitionParser() {
        super("./data/items/item_defs.json");
    }

    @Override
    public ItemDefinition readObject(JsonObject reader) throws Exception {
        int id = reader.get("id").getAsInt();
        String name = reader.get("name").getAsString();
        String examine = reader.get("examine").getAsString();
        int equipmentSlot = reader.get("equipment_slot").getAsInt();
        boolean noteable = reader.get("noteable").getAsBoolean();
        boolean stackable = reader.get("stackable").getAsBoolean();
        int specialValue = reader.get("special_value").getAsInt();
        int generalValue = reader.get("general_value").getAsInt();
        int lowAlchValue = reader.get("low_alchemy_value").getAsInt();
        int highAlchValue = reader.get("high_alchemy_value").getAsInt();
        double weight = reader.get("weight").getAsInt();
        int[] bonus = GsonUtils.getAsType(reader.get("bonuses"), int[].class);
        boolean twoHanded = reader.get("two_handed").getAsBoolean();
        boolean fullHelm = reader.get("full_helmet").getAsBoolean();
        boolean platebody = reader.get("platebody").getAsBoolean();
        boolean tradeable = reader.get("tradeable").getAsBoolean();
        return new ItemDefinition(id, name, examine, equipmentSlot, noteable, stackable, specialValue, generalValue,
            lowAlchValue, highAlchValue, weight, bonus, twoHanded, fullHelm, platebody, tradeable);
    }

    @Override
    public void onReadComplete(List<ItemDefinition> readObjects) throws Exception {
        readObjects.forEach(it -> ItemDefinition.DEFINITIONS[it.getId()] = it);
    }
}