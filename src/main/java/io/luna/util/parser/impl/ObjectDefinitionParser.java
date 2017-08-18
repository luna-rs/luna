package io.luna.util.parser.impl;

import com.google.gson.JsonObject;
import io.luna.game.model.def.ObjectDefinition;
import io.luna.util.GsonUtils;
import io.luna.util.parser.GsonParser;

import java.util.List;

/**
 * A {@link GsonParser} implementation that reads Object definitions.
 *
 * @author Trevor Flynn {@literal <trevorflynn@liquidcrystalstudios.com>}
 */
public final class ObjectDefinitionParser extends GsonParser<ObjectDefinition> {

    /**
     * Creates a new {@link ObjectDefinitionParser}.
     */
    public ObjectDefinitionParser() {
        super("./data/objects/obj_defs.json");
    }

    @Override
    public ObjectDefinition readObject(JsonObject reader) throws Exception {
        int id = reader.get("id").getAsInt();
        String name = reader.get("name").getAsString();
        String examine = reader.get("examine").getAsString();
        int length = reader.get("length").getAsInt();
        int width = reader.get("width").getAsInt();
        boolean isImp = reader.get("impenetrable?").getAsBoolean();
        boolean isInt = reader.get("interactive?").getAsBoolean();
        boolean isObs = reader.get("obstructive?").getAsBoolean();
        boolean isSolid = reader.get("solid?").getAsBoolean();
        String[] actions = GsonUtils.getAsType(reader.get("actions"), String[].class);

        return new ObjectDefinition(id, name, examine, length, width, isImp, isInt, isObs, isSolid, actions);
    }

    @Override
    public void onReadComplete(List<ObjectDefinition> readObjects) throws Exception {
        ObjectDefinition[] definitions = new ObjectDefinition[14974];
        readObjects.forEach(def -> definitions[def.getId()] = def);

        ObjectDefinition.set(definitions);
    }
}