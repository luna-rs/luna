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
     * An array that will contain parsed {@link ItemDefinition}s.
     */
    private final ItemDefinition[] definitions;

    /**
     * Creates a new {@link ItemDefinitionParser}.
     *
     * @param definitions An array that will contain parsed {@link ItemDefinition}s.
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
        int baseValue = reader.get("base_value").getAsInt();
        int specialValue = reader.get("special_value").getAsInt();
        int notedId = reader.get("noted_id").getAsInt();
        int unnotedId = reader.get("unnoted_id").getAsInt();
        boolean membersOnly = reader.get("members_only").getAsBoolean();
        double weight = reader.get("weight").getAsDouble();
        boolean tradable = reader.get("tradeable").getAsBoolean();
        String[] inventoryActions = GsonUtils.getAsType(reader.get("inventory_actions"), String[].class);
        String[] groundActions = GsonUtils.getAsType(reader.get("ground_actions"), String[].class);

        return new ItemDefinition(id, name, examine, stackable, baseValue, specialValue, notedId, unnotedId, membersOnly,
            weight, tradable, inventoryActions, groundActions);
    }

    @Override
    public void onReadComplete(List<ItemDefinition> readObjects) throws Exception {
        readObjects.forEach(it -> definitions[it.getId()] = it);
    }
}