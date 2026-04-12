package io.luna.util.parser.impl;

import com.google.common.collect.ImmutableList;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.luna.game.model.def.NpcCombatDefinition;
import io.luna.game.model.mob.NpcAggressionProfile;
import io.luna.game.model.mob.NpcAggressionProfile.NpcAggressionPolicy;
import io.luna.util.GsonUtils;
import io.luna.util.parser.JsonFileParser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Paths;

import static org.apache.logging.log4j.util.Unbox.box;

/**
 * A {@link JsonFileParser} implementation that reads NPC combat definitions.
 *
 * @author lare96
 */
public final class NpcCombatDefinitionFileParser extends JsonFileParser<NpcCombatDefinition> {

    /**
     * The logger.
     */
    private static final Logger logger = LogManager.getLogger();

    /**
     * Creates a new {@link NpcCombatDefinitionFileParser}.
     */
    public NpcCombatDefinitionFileParser() {
        super(Paths.get("data", "game", "def", "npcs", "npc_combat.json"));
    }

    @Override
    public NpcCombatDefinition convert(JsonObject token) {
        int id = token.get("id").getAsInt();
        int respawnTicks = token.get("respawn_ticks").getAsInt();
        NpcAggressionProfile aggression = readProfile(token.get("aggression"));
        boolean poisonous = token.get("poisonous").getAsBoolean();
        boolean immunePoison = token.get("immune_poison").getAsBoolean();
        int hitpoints = token.get("hitpoints").getAsInt();
        int maximumHit = token.get("maximum_hit").getAsInt();
        int attackSpeed = token.get("attack_speed").getAsInt();
        int attackAnimation = token.get("attack_animation").getAsInt();
        int defenceAnimation = token.get("defence_animation").getAsInt();
        int deathAnimation = token.get("death_animation").getAsInt();
        int attackBonus = token.get("attack_bonus").getAsInt();
        int magicBonus = token.get("magic_bonus").getAsInt();
        int rangedBonus = token.get("ranged_bonus").getAsInt();
        int[] skills = GsonUtils.getAsType(token.get("skills"), int[].class);
        int[] bonuses = GsonUtils.getAsType(token.get("bonuses"), int[].class);
        return new NpcCombatDefinition(id, respawnTicks, aggression, poisonous, immunePoison, hitpoints, maximumHit,
                attackSpeed, attackAnimation, defenceAnimation, deathAnimation, attackBonus, magicBonus,
                rangedBonus, skills, bonuses);
    }

    @Override
    public void onCompleted(ImmutableList<NpcCombatDefinition> tokenObjects) {
        NpcCombatDefinition.ALL.storeAndLock(tokenObjects);
        logger.debug("Loaded {} NPC combat definitions!", box(tokenObjects.size()));
    }

    private NpcAggressionProfile readProfile(JsonElement element) {
        if (element.isJsonNull()) {
            return null;
        }
        JsonObject object = element.getAsJsonObject();
        NpcAggressionPolicy policy = NpcAggressionPolicy.valueOf(object.get("policy").getAsString());
        int toleranceMinutes = object.get("tolerance_minutes").getAsInt();
        return new NpcAggressionProfile(policy, toleranceMinutes);
    }
}
