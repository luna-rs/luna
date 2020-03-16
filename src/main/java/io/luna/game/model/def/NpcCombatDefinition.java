package io.luna.game.model.def;

import java.util.Arrays;

/**
 * A definition model describing an attackable non-player.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class NpcCombatDefinition implements Definition {

    /**
     * The attack skill index.
     */
    public static final int ATTACK = 0;

    /**
     * The strength skill index.
     */
    public static final int STRENGTH = 1;

    /**
     * The defence skill index.
     */
    public static final int DEFENCE = 2;

    /**
     * The ranged skill index.
     */
    public static final int RANGED = 3;

    /**
     * The magic skill index.
     */
    public static final int MAGIC = 4;

    /**
     * The stab attack bonus index.
     */
    public static final int STAB_ATTACK = 0;

    /**
     * The slash attack bonus index.
     */
    public static final int SLASH_ATTACK = 1;

    /**
     * The crush attack bonus index.
     */
    public static final int CRUSH_ATTACK = 2;

    /**
     * The magic attack bonus index.
     */
    public static final int MAGIC_ATTACK = 3;

    /**
     * The ranged attack bonus index.
     */
    public static final int RANGED_ATTACK = 4;

    /**
     * The stab defence bonus index.
     */
    public static final int STAB_DEFENCE = 5;

    /**
     * The slash defence bonus index.
     */
    public static final int SLASH_DEFENCE = 6;

    /**
     * The crush defence bonus index.
     */
    public static final int CRUSH_DEFENCE = 7;

    /**
     * The magic defence bonus index.
     */
    public static final int MAGIC_DEFENCE = 8;

    /**
     * The ranged defence bonus index.
     */
    public static final int RANGED_DEFENCE = 9;

    /**
     * The NPC combat definition repository.
     */
    public static final DefinitionRepository<NpcCombatDefinition> ALL = new MapDefinitionRepository<>();

    /**
     * The identifier.
     */
    private final int id;

    /**
     * The respawn time (in ticks).
     */
    private final int respawnTime;

    /**
     * If the non-player is aggressive.
     */
    private final boolean aggressive;

    /**
     * If the non-player is poisonous.
     */
    private final boolean poisonous;

    /**
     * The combat level.
     */
    private final int level;

    /**
     * The hitpoint amount.
     */
    private final int hitpoints;

    /**
     * The maximum hit.
     */
    private final int maximumHit;

    /**
     * The attack speed.
     */
    private final int attackSpeed;

    /**
     * The attack animation.
     */
    private final int attackAnimation;

    /**
     * The defence animation
     */
    private final int defenceAnimation;

    /**
     * The death animation.
     */
    private final int deathAnimation;

    /**
     * An array of skills.
     */
    private final int[] skills;

    /**
     * An array of bonuses.
     */
    private final int[] bonuses;

    /**
     * Creates a new {@link NpcCombatDefinition}.
     *
     * @param id The identifier.
     * @param respawnTime The respawn time (in ticks).
     * @param aggressive If the non-player is aggressive.
     * @param poisonous If the non-player is poisonous.
     * @param level The combat level.
     * @param hitpoints The hitpoint amount.
     * @param maximumHit The maximum hit.
     * @param attackSpeed The attack speed.
     * @param attackAnimation The attack animation.
     * @param defenceAnimation The defence animation
     * @param deathAnimation The death animation.
     * @param skills A list of skills.
     * @param bonuses A list of bonuses.
     */
    public NpcCombatDefinition(int id, int respawnTime, boolean aggressive, boolean poisonous, int level,
                               int hitpoints, int maximumHit, int attackSpeed, int attackAnimation, int defenceAnimation,
                               int deathAnimation, int[] skills, int[] bonuses) {
        this.id = id;
        this.respawnTime = respawnTime;
        this.aggressive = aggressive;
        this.poisonous = poisonous;
        this.level = level;
        this.hitpoints = hitpoints;
        this.maximumHit = maximumHit;
        this.attackSpeed = attackSpeed;
        this.attackAnimation = attackAnimation;
        this.defenceAnimation = defenceAnimation;
        this.deathAnimation = deathAnimation;
        this.skills = Arrays.copyOf(skills, skills.length);
        this.bonuses = Arrays.copyOf(bonuses, bonuses.length);
    }

    @Override
    public int getId() {
        return id;
    }

    /**
     * @return The respawn time (in ticks).
     */
    public int getRespawnTime() {
        return respawnTime;
    }

    /**
     * @return If the non-player is aggressive.
     */
    public boolean isAggressive() {
        return aggressive;
    }

    /**
     * @return If the non-player is poisonous.
     */
    public boolean isPoisonous() {
        return poisonous;
    }

    /**
     * @return The combat level.
     */
    public int getLevel() {
        return level;
    }

    /**
     * @return The hitpoint amount.
     */
    public int getHitpoints() {
        return hitpoints;
    }

    /**
     * @return The maximum hit.
     */
    public int getMaximumHit() {
        return maximumHit;
    }

    /**
     * @return The attack speed.
     */
    public int getAttackSpeed() {
        return attackSpeed;
    }

    /**
     * @return The attack animation.
     */
    public int getAttackAnimation() {
        return attackAnimation;
    }

    /**
     * @return The defence animation.
     */
    public int getDefenceAnimation() {
        return defenceAnimation;
    }

    /**
     * @return The death animation.
     */
    public int getDeathAnimation() {
        return deathAnimation;
    }
    
    /**
     * Gets the skill at the specified identifier.
     *
     * @param id The skill identifier.
     * @return The skill at the specified identifier as an {@code int}.
     */
    public int getSkill(int id) {
        return skills[id];
    }
}
