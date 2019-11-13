package io.luna.util.parser.impl;

import com.google.common.collect.ImmutableList;
import com.google.gson.JsonObject;
import io.luna.game.model.def.ObjectDefinition;
import io.luna.util.GsonUtils;
import io.luna.util.parser.AbstractJsonFileParser;

/**
 * A {@link AbstractJsonFileParser} implementation that reads Object definitions.
 *
 * @author Trevor Flynn {@literal <trevorflynn@liquidcrystalstudios.com>}
 */
public final class ObjectDefinitionFileParser extends AbstractJsonFileParser<ObjectDefinition> {

    /**
     * Creates a new {@link ObjectDefinitionFileParser}.
     */
    public ObjectDefinitionFileParser() {
        super("./data/def/objects/obj_defs.json");
    }

    @Override
    public ObjectDefinition convert(JsonObject token) throws Exception {
        int id = token.get("id").getAsInt();
        String name = token.get("name").getAsString();
        String examine = token.get("examine").getAsString();
        int length = token.get("length").getAsInt();
        int width = token.get("width").getAsInt();
        boolean isImp = token.get("impenetrable?").getAsBoolean();
        boolean isInt = token.get("interactive?").getAsBoolean();
        boolean isObs = token.get("obstructive?").getAsBoolean();
        boolean isSolid = token.get("solid?").getAsBoolean();
        String[] actions = GsonUtils.getAsType(token.get("actions"), String[].class);
        return new ObjectDefinition(id, name, examine, length, width, isImp, isInt, isObs, isSolid, actions);
    }

    @Override
    public void onCompleted(ImmutableList<ObjectDefinition> tokenObjects) throws Exception {
        tokenObjects.forEach(ObjectDefinition.ALL::storeDefinition);
    }
}
