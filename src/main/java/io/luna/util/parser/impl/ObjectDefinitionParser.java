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
     * The array of parsed definitions.
     */
    private final ObjectDefinition[] definitions;

    /**
     * Creates a new {@link ObjectDefinitionParser}.
     *
     * @param definitions The array of parsed definitions.
     */
    public ObjectDefinitionParser(ObjectDefinition[] definitions) {
        super("./data/objects/obj_defs.json");
        this.definitions = definitions;
    }

    @Override
    public ObjectDefinition readObject(JsonObject reader) throws Exception {
        int id = reader.get("id").getAsInt();
        String name = reader.get("name").getAsString();
        String examine = reader.get("examine").getAsString();
        int length = reader.get("length").getAsInt();
        int width = reader.get("width").getAsInt();
        String[] actions = GsonUtils.getAsType(reader.get("actions"), String[].class);
        boolean isImp = reader.get("is_impenetrable").getAsBoolean();
        boolean isInt = reader.get("is_interactive").getAsBoolean();
        boolean isObs = reader.get("is_obstructive").getAsBoolean();
        boolean isSolid = reader.get("is_solid").getAsBoolean();
        return new ObjectDefinition(id, name, examine, isSolid, isImp, isObs, isInt, width, length, actions);
    }

    @Override
    public void onReadComplete(List<ObjectDefinition> readObjects) throws Exception {
        readObjects.forEach(it -> definitions[it.getId()] = it);
    }
}