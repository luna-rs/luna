package io.luna.util.parser.impl;

import com.google.common.collect.ImmutableList;
import com.google.gson.JsonObject;
import io.luna.game.model.def.ItemDefinition;
import io.luna.util.GsonUtils;
import io.luna.util.parser.GsonParser;

import java.util.List;

/**
 * A {@link GsonParser} implementation that reads item definitions.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class ItemDefinitionParser extends GsonParser<ItemDefinition> {

    /**
     * A String array of empty inventory and ground actions.
     */
    // Do this in def class as the array is copied anyway
    private final String[] empty = new String[]{"null", "null", "null", "null", "null"};

    @Override
    public ItemDefinition readObject(JsonObject reader) throws Exception {
        int id = reader.get("id").getAsInt();
        String name = reader.get("name").getAsString();
        String examine = reader.get("examine").getAsString();
        boolean stackable = reader.get("stack?").getAsBoolean();
        int baseValue = reader.get("value").getAsInt();
        int notedId = reader.get("noted_id").getAsInt();
        int unnotedId = reader.get("unnoted_id").getAsInt();
        boolean membersOnly = reader.get("members_only?").getAsBoolean();
        double weight = reader.get("weight").getAsDouble();
        boolean tradeable = reader.get("trade?").getAsBoolean();
        String[] inventoryActions = GsonUtils.getAsType(reader.get("inventory_actions"), String[].class);
        String[] groundActions = GsonUtils.getAsType(reader.get("ground_actions"), String[].class);

        inventoryActions = inventoryActions.length == 0 ? empty : inventoryActions;
        groundActions = groundActions.length == 0 ? empty : groundActions;

        return new ItemDefinition(id, name, examine, stackable, baseValue, notedId, unnotedId, membersOnly, weight,
                tradeable, inventoryActions, groundActions);
    }

    @Override
    public void onReadComplete(List<ItemDefinition> readObjects) throws Exception {
        readObjects.forEach(ItemDefinition.ALL::storeDefinition);
    }

    @Override
    public ImmutableList<String> forFiles() {
        return ImmutableList.of("./data/items/item_defs.json");
    }
}