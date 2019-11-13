package io.luna.util.parser.impl;

import com.google.common.collect.ImmutableList;
import com.google.gson.JsonObject;
import io.luna.game.model.def.NpcDefinition;
import io.luna.util.GsonUtils;
import io.luna.util.parser.AbstractJsonFileParser;

/**
 * A {@link AbstractJsonFileParser} implementation that reads NPC definitions.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class NpcDefinitionFileParser extends AbstractJsonFileParser<NpcDefinition> {

    /**
     * Creates a new {@link NpcDefinitionFileParser}.
     */
    public NpcDefinitionFileParser() {
        super("./data/def/npcs/npc_defs.json");
    }

    @Override
    public NpcDefinition convert(JsonObject token) throws Exception {
        int id = token.get("id").getAsInt();
        String name = token.get("name").getAsString();
        String examine = token.get("examine").getAsString();
        int size = token.get("size").getAsInt();
        int walkAnimation = token.get("walk_animation").getAsInt();
        int walkBackAnimation = token.get("walk_back_animation").getAsInt();
        int walkLeftAnimation = token.get("walk_left_animation").getAsInt();
        int walkRightAnimation = token.get("walk_right_animation").getAsInt();
        String[] actions = GsonUtils.getAsType(token.get("actions"), String[].class);
        return new NpcDefinition(id, name, examine, size, walkAnimation, walkBackAnimation, walkLeftAnimation,
                walkRightAnimation, actions);
    }

    @Override
    public void onCompleted(ImmutableList<NpcDefinition> tokenObjects) throws Exception {
        tokenObjects.forEach(NpcDefinition.ALL::storeDefinition);
    }
}
