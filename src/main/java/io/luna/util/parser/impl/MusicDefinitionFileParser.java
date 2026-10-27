package io.luna.util.parser.impl;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.luna.game.model.def.MusicDefinition;
import io.luna.game.model.def.MusicDefinition.MusicRegion;
import io.luna.game.model.def.MusicLibrary;
import io.luna.game.model.mob.varp.PersistentVarp;
import io.luna.util.parser.JsonFileParser;

import java.nio.file.Paths;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Loads the 377 music tab, unlock bits and area mappings derived from Lost City.
 *
 * @author lare96
 */
public final class MusicDefinitionFileParser extends JsonFileParser<MusicDefinition> {

    public MusicDefinitionFileParser() {
        super(Paths.get("data", "game", "def", "music.jsonc"));
    }

    @Override
    public MusicDefinition convert(JsonObject token) {
        int id = token.get("id").getAsInt();
        String name = token.get("name").getAsString();
        int button = token.get("button").getAsInt();
        PersistentVarp varp = null;
        int bit = -1;
        if (token.has("unlock")) {
            var unlock = token.getAsJsonArray("unlock");
            checkArgument(unlock.size() == 2, "Expected [word, bit] for music %s", id);
            int word = unlock.get(0).getAsInt();
            checkArgument(word >= 1 && word <= 14, "Invalid music unlock word: %s", word);
            varp = PersistentVarp.valueOf("MUSIC_UNLOCKS_" + word);
            bit = unlock.get(1).getAsInt();
        }
        var regions = ImmutableSet.<MusicRegion>builder();
        for (JsonElement entry : token.getAsJsonArray("regions")) {
            var region = entry.getAsJsonArray();
            checkArgument(region.size() == 2, "Expected [region, plane] for music %s", id);
            regions.add(new MusicRegion(region.get(0).getAsInt(), region.get(1).getAsInt()));
        }
        return new MusicDefinition(id, name, button, button, regions.build(), varp, bit);
    }

    @Override
    public void onCompleted(ImmutableList<MusicDefinition> definitions) {
        MusicLibrary.load(definitions);
    }
}
