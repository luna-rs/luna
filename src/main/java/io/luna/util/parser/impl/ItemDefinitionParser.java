package io.luna.util.parser.impl;

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
     * An array of parsed definitions.
     */
    private final ItemDefinition[] definitions;

    /**
     * Creates a new {@link ItemDefinitionParser}.
     *
     * @param definitions An array of parsed definitions.
     */
    public ItemDefinitionParser(ItemDefinition[] definitions) {
        super("./data/items/item_defs.json");
        this.definitions = definitions;
    }

    @Override
    public ItemDefinition readObject(JsonObject reader) throws Exception {
        int id = reader.get("id").getAsInt();
        String name = reader.get("name").getAsString();
        String examine = reader.get("examine").getAsString();
        boolean stackable = reader.get("stackable").getAsBoolean();
        int baseValue = reader.get("value").getAsInt();
        int notedId = reader.get("noted_id").getAsInt();
        int unnotedId = reader.get("unnoted_id").getAsInt();
        boolean membersOnly = reader.get("members_only").getAsBoolean();
        double weight = reader.get("weight").getAsDouble();
        boolean tradable = reader.get("tradeable").getAsBoolean();
        String[] inventoryActions = GsonUtils.getAsType(reader.get("inventory_actions"), String[].class);
        String[] groundActions = GsonUtils.getAsType(reader.get("ground_actions"), String[].class);

        for (int index = 0; index < inventoryActions.length; index++) {
            if (inventoryActions[index] == null) {
                inventoryActions[index] = "";
            }
        }
        for (int index = 0; index < groundActions.length; index++) {
            if (groundActions[index] == null) {
                groundActions[index] = "";
            }
        }

        return new ItemDefinition(id, name, examine, stackable, baseValue, notedId, unnotedId, membersOnly, weight,
            tradable, inventoryActions, groundActions);
    }

    @Override
    public void onReadComplete(List<ItemDefinition> readObjects) throws Exception {
        readObjects.forEach(it -> definitions[it.getId()] = it);
    }
}