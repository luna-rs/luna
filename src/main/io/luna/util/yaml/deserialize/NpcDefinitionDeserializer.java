package io.luna.util.yaml.deserialize;

import io.luna.game.model.def.NpcDefinition;
import io.luna.util.yaml.YamlDeserializer;
import io.luna.util.yaml.YamlDocument;

import java.util.List;

/**
 * A {@link YamlDeserializer} implementation that deserializes {@code YAML}
 * files into {@link NpcDefinition}s.
 * 
 * @author lare96 <http://github.org/lare96>
 */
public final class NpcDefinitionDeserializer extends YamlDeserializer<NpcDefinition> {

    /**
     * Creates a new {@link NpcDefinitionDeserializer}.
     */
    public NpcDefinitionDeserializer() {
        super("./data/npcs/npc_defs.yml");
    }

    @Override
    public NpcDefinition deserialize(YamlDocument yml) throws Exception {
        int id = yml.get("id").asInt();
        String name = yml.get("name").asString();
        String description = yml.get("examine").asString();
        int combatLevel = yml.get("combat_level").asInt();
        int size = yml.get("size").asInt();
        boolean attackable = yml.get("attackable").asBoolean();
        boolean aggressive = yml.get("aggressive").asBoolean();
        boolean retreats = yml.get("retreats").asBoolean();
        boolean poisonous = yml.get("poisonous").asBoolean();
        int respawnTime = yml.get("respawn_time").asInt();
        int maxHit = yml.get("maximum_hit").asInt();
        int hitpoints = yml.get("hitpoints").asInt();
        int attackSpeed = yml.get("attack_speed").asInt();
        int attackAnim = yml.get("attack_animation").asInt();
        int defenceAnim = yml.get("defence_animation").asInt();
        int deathAnim = yml.get("death_animation").asInt();
        int attackBonus = yml.get("attack_bonus").asInt();
        int meleeDefence = yml.get("melee_defence").asInt();
        int rangedDefence = yml.get("ranged_defence").asInt();
        int magicDefence = yml.get("magic_defence").asInt();
        return new NpcDefinition(id, name, description, combatLevel, size, attackable, aggressive, retreats, poisonous, respawnTime, maxHit, hitpoints, attackSpeed,
            attackAnim, defenceAnim, deathAnim, attackBonus, meleeDefence, rangedDefence, magicDefence);
    }

    @Override
    public void onComplete(List<NpcDefinition> list) {
        list.forEach(it -> NpcDefinition.DEFINITIONS[it.getId()] = it);
    }
}