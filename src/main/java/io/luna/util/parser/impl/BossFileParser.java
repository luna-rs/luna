package io.luna.util.parser.impl;

import com.google.common.collect.ImmutableList;
import com.google.gson.JsonObject;
import io.luna.game.model.mob.combat.state.NpcCombatContext;
import io.luna.util.parser.JsonFileParser;

import java.nio.file.Paths;
import java.util.HashSet;

/**
 * Parses the npc boss id definition file and loads the resulting ids into {@link NpcCombatContext}.
 *
 * @author lare96
 */
public final class BossFileParser extends JsonFileParser<Integer> {

    /**
     * Creates a new {@link BossFileParser}.
     */
    public BossFileParser() {
        super(Paths.get("data", "game", "def", "npcs", "bosses.jsonc"));
    }

    @Override
    public Integer convert(JsonObject token) {
        return token.getAsInt();
    }

    @Override
    public void onCompleted(ImmutableList<Integer> tokenObjects) {
        NpcCombatContext.setBosses(new HashSet<>(tokenObjects));
    }
}