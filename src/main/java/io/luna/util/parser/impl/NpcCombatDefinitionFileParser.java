package io.luna.util.parser.impl;

import com.google.common.collect.ImmutableList;
import com.google.gson.JsonObject;
import io.luna.game.model.def.NpcCombatDefinition;
import io.luna.util.GsonUtils;
import io.luna.util.parser.AbstractJsonFileParser;

/**
 * A {@link AbstractJsonFileParser} implementation that reads NPC combat definitions.
 *
 * @author lare96 <http://github.org/lare96>
 */
public class NpcCombatDefinitionFileParser extends AbstractJsonFileParser<NpcCombatDefinition> {

    /**
     * Creates a new {@link NpcCombatDefinitionFileParser}.
     */
    public NpcCombatDefinitionFileParser() {
        super("./data/def/npcs/npc_combat_defs.json");
    }

    @Override
    public NpcCombatDefinition convert(JsonObject token) throws Exception {
        int id = token.get("id").getAsInt();
        int respawnTicks = token.get("respawn_ticks").getAsInt();
        boolean aggressive = token.get("aggressive?").getAsBoolean();
        boolean poisonous = token.get("poisonous?").getAsBoolean();
        int combatLevel = token.get("level").getAsInt();
        int hitpoints = token.get("hitpoints").getAsInt();
        int maximumHit = token.get("maximum_hit").getAsInt();
        int attackSpeed = token.get("attack_speed").getAsInt();
        int attackAnimation = token.get("attack_animation").getAsInt();
        int defenceAnimation = token.get("defence_animation").getAsInt();
        int deathAnimation = token.get("death_animation").getAsInt();
        int[] skills = GsonUtils.getAsType(token.get("skills"), int[].class);
        int[] bonuses = GsonUtils.getAsType(token.get("bonuses"), int[].class);
        return new NpcCombatDefinition(id, respawnTicks, aggressive, poisonous, combatLevel, hitpoints, maximumHit,
                attackSpeed, attackAnimation, defenceAnimation, deathAnimation, skills, bonuses);
    }

    @Override
    public void onCompleted(ImmutableList<NpcCombatDefinition> tokenObjects) throws Exception {
        NpcCombatDefinition.ALL.storeAndLock(tokenObjects);
    }
}
