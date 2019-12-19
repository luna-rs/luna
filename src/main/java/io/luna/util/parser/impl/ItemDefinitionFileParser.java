package io.luna.util.parser.impl;

import com.google.common.collect.ImmutableList;
import com.google.gson.JsonObject;
import io.luna.game.model.def.ItemDefinition;
import io.luna.util.GsonUtils;
import io.luna.util.parser.AbstractJsonFileParser;

/**
 * A {@link AbstractJsonFileParser} implementation that reads item definitions.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class ItemDefinitionFileParser extends AbstractJsonFileParser<ItemDefinition> {

    /**
     * Creates a new {@link ItemDefinitionFileParser}.
     */
    public ItemDefinitionFileParser() {
        super("./data/def/items/item_defs.json");
    }

    @Override
    public ItemDefinition convert(JsonObject token) throws Exception {
        int id = token.get("id").getAsInt();
        String name = token.get("name").getAsString();
        boolean stackable = token.get("stack?").getAsBoolean();
        int baseValue = token.get("value").getAsInt();
        int notedId = token.get("noted_id").getAsInt();
        int unnotedId = token.get("unnoted_id").getAsInt();
        boolean membersOnly = token.get("members_only?").getAsBoolean();
        double weight = token.get("weight").getAsDouble();
        boolean tradeable = token.get("trade?").getAsBoolean();
        String[] inventoryActions = GsonUtils.getAsType(token.get("inventory_actions"), String[].class);
        String[] groundActions = GsonUtils.getAsType(token.get("ground_actions"), String[].class);
        return new ItemDefinition(id, name, stackable,baseValue, notedId, unnotedId, membersOnly, weight,
                tradeable, inventoryActions, groundActions);
    }

    @Override
    public void onCompleted(ImmutableList<ItemDefinition> tokenObjects) throws Exception {
        ItemDefinition.ALL.storeAndLock(tokenObjects);
    }
}
