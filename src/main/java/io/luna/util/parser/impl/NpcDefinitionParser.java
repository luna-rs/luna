package io.luna.util.parser.impl;

import com.google.gson.JsonObject;
import io.luna.game.model.def.NpcDefinition;
import io.luna.util.parser.GsonParser;

import java.util.List;

/**
 * A {@link GsonParser} implementation that reads {@link NpcDefinition}s.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class NpcDefinitionParser extends GsonParser<NpcDefinition> {

    /**
     * Creates a new {@link NpcDefinitionParser}.
     */
    public NpcDefinitionParser() {
        super("./data/npcs/npc_defs.json");
    }

    @Override
    public NpcDefinition readObject(JsonObject reader) throws Exception {
        int id = reader.get("id").getAsInt();
        String name = reader.get("name").getAsString();
        String description = reader.get("examine").getAsString();
        int combatLevel = reader.get("combat_level").getAsInt();
        int size = reader.get("size").getAsInt();
        boolean attackable = reader.get("attackable").getAsBoolean();
        boolean aggressive = reader.get("aggressive").getAsBoolean();
        boolean retreats = reader.get("retreats").getAsBoolean();
        boolean poisonous = reader.get("poisonous").getAsBoolean();
        int respawnTime = reader.get("respawn_time").getAsInt();
        int maxHit = reader.get("maximum_hit").getAsInt();
        int hitpoints = reader.get("hitpoints").getAsInt();
        int attackSpeed = reader.get("attack_speed").getAsInt();
        int attackAnim = reader.get("attack_animation").getAsInt();
        int defenceAnim = reader.get("defence_animation").getAsInt();
        int deathAnim = reader.get("death_animation").getAsInt();
        int attackBonus = reader.get("attack_bonus").getAsInt();
        int meleeDefence = reader.get("melee_defence").getAsInt();
        int rangedDefence = reader.get("ranged_defence").getAsInt();
        int magicDefence = reader.get("magic_defence").getAsInt();
        return new NpcDefinition(id, name, description, combatLevel, size, attackable, aggressive, retreats, poisonous,
            respawnTime, maxHit, hitpoints, attackSpeed, attackAnim, defenceAnim, deathAnim, attackBonus, meleeDefence,
            rangedDefence, magicDefence);
    }

    @Override
    public void onReadComplete(List<NpcDefinition> readObjects) throws Exception {
        readObjects.forEach(it -> NpcDefinition.DEFINITIONS[it.getId()] = it);
    }
}