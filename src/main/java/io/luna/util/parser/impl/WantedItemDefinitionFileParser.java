package io.luna.util.parser.impl;

import com.google.common.collect.ImmutableList;
import com.google.gson.JsonObject;
import io.luna.game.model.def.WantedItemDefinition;
import io.luna.util.parser.JsonFileParser;

import java.nio.file.Paths;

/**
 * Parses the default wanted-item definitions used by bots.
 *
 * @author lare96
 */
public final class WantedItemDefinitionFileParser extends JsonFileParser<WantedItemDefinition> {

    /**
     * Creates a {@link WantedItemDefinitionFileParser} for the default bot wanted-items file.
     */
    public WantedItemDefinitionFileParser() {
        super(Paths.get("data", "game", "bots", "items", "default_wanted_items.jsonc"));
    }

    @Override
    public WantedItemDefinition convert(JsonObject token) {
        int id = token.get("id").getAsInt();
        int min = token.get("min").getAsInt();
        int target = token.get("target").getAsInt();
        int skill = token.get("skill").getAsInt();
        int maxLevel = token.get("max_level").getAsInt();
        return new WantedItemDefinition(id, min, target, skill, maxLevel);
    }

    @Override
    public void onCompleted(ImmutableList<WantedItemDefinition> tokenObjects) {
        WantedItemDefinition.DEFAULT.storeAndLock(tokenObjects);
    }
}