package io.luna.util.parser.impl;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.gson.JsonObject;
import io.luna.game.model.Region;
import io.luna.game.model.def.MusicDefinition;
import io.luna.util.GsonUtils;
import io.luna.util.parser.JsonFileParser;

import java.nio.file.Paths;

/**
 * A {@link JsonFileParser} implementation that reads music definitions.
 *
 * @author lare96
 */
public final class MusicDefinitionFileParser extends JsonFileParser<MusicDefinition> {

    /**
     * Creates a new {@link MusicDefinitionFileParser}.
     */
    public MusicDefinitionFileParser() {
        super(Paths.get("data", "game", "def", "music.json"));
    }

    @Override
    public MusicDefinition convert(JsonObject token) {
        int id = token.get("id").getAsInt();
        String name = token.get("name").getAsString();
        int lineId = token.get("line_id").getAsInt();
        int buttonId = token.get("button_id").getAsInt();
        ImmutableSet<Region> regions = ImmutableSet.copyOf(GsonUtils.getAsType(token.get("regions"), Integer[].class)).
                stream().map(Region::new).collect(ImmutableSet.toImmutableSet());
        return new MusicDefinition(id, name, lineId, buttonId, regions);
    }

    @Override
    public void onCompleted(ImmutableList<MusicDefinition> tokenObjects) {
        MusicDefinition.ALL.storeAndLock(tokenObjects);
    }
}

