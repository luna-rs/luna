package io.luna.util.parser.impl;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.gson.JsonObject;
import io.luna.game.model.def.ItemDefinition;
import io.luna.game.model.def.MusicDefinition;
import io.luna.util.GsonUtils;
import io.luna.util.parser.AbstractJsonFileParser;

/**
 * A {@link AbstractJsonFileParser} implementation that reads music definitions.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class MusicDefinitionFileParser extends AbstractJsonFileParser<MusicDefinition> {

    /**
     * Creates a new {@link MusicDefinitionFileParser}.
     */
    public MusicDefinitionFileParser() {
        super("./data/def/audio/music.json");
    }

    @Override
    public MusicDefinition convert(JsonObject token) throws Exception {
        int id = token.get("id").getAsInt();
        String name = token.get("name").getAsString();
        int lineId = token.get("line_id").getAsInt();
        int buttonId = token.get("button_id").getAsInt();
        ImmutableSet<Integer> regions = ImmutableSet.
                copyOf(GsonUtils.getAsType(token.get("regions"), Integer[].class));
        return new MusicDefinition(id, name, lineId, buttonId, regions);
    }

    @Override
    public void onCompleted(ImmutableList<MusicDefinition> tokenObjects) throws Exception {
        MusicDefinition.ALL.storeAndLock(tokenObjects);
    }
}

