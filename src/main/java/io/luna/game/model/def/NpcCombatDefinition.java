package io.luna.game.model.def;

import java.util.Arrays;

/**
 * A definition describing combat-related data for an attackable NPC.
 * <p>
 * This definition complements {@link NpcDefinition} by providing the data used by combat/respawn systems, including:
 * <ul>
 *     <li>respawn timing</li>
 *     <li>aggression and poison flags</li>
 *     <li>combat level, hitpoints, max hit, and attack speed</li>
 *     <li>combat animations (attack/defence/death)</li>
 *     <li>combat “skill levels” (attack/strength/defence/ranged/magic)</li>
 *     <li>combat bonuses (attack + defence bonus arrays in style order)</li>
 * </ul>
 *
 * @author lare96
 */
public final class NpcCombatDefinition implements Definition {

    /* -------- Skill index constants -------- */

    /**
     * Skill array index: Attack.
     */
    public static final int ATTACK = 0;

    /**
     * Skill array index: Strength.
     */
    public static final int STRENGTH = 1;

    /**
     * Skill array index: Defence.
     */
    public static final int DEFENCE = 2;

    /**
     * Skill array index: Ranged.
     */
    public static final int RANGED = 3;

    /**
     * Skill array index: Magic.
     */
    public static final int MAGIC = 4;

    /* -------- Bonus index constants -------- */

    /**
     * Bonus array index: stab attack.
     */
    public static final int STAB_ATTACK = 0;

    /**
     * Bonus array index: slash attack.
     */
    public static final int SLASH_ATTACK = 1;

    /**
     * Bonus array index: crush attack.
     */
    public static final int CRUSH_ATTACK = 2;

    /**
     * Bonus array index: magic attack.
     */
    public static final int MAGIC_ATTACK = 3;

    /**
     * Bonus array index: ranged attack.
     */
    public static final int RANGED_ATTACK = 4;

    /**
     * Bonus array index: stab defence.
     */
    public static final int STAB_DEFENCE = 5;

    /**
     * Bonus array index: slash defence.
     */
    public static final int SLASH_DEFENCE = 6;

    /**
     * Bonus array index: crush defence.
     */
    public static final int CRUSH_DEFENCE = 7;

    /**
     * Bonus array index: magic defence.
     */
    public static final int MAGIC_DEFENCE = 8;

    /**
     * Bonus array index: ranged defence.
     */
    public static final int RANGED_DEFENCE = 9;

    /**
     * Repository of {@link NpcCombatDefinition}s keyed by NPC id.
     */
    public static final DefinitionRepository<NpcCombatDefinition> ALL = new MapDefinitionRepository<>();

    /**
     * The NPC id this combat definition belongs to.
     */
    private final int id;

    /**
     * Respawn delay in ticks after death.
     */
    private final int respawnTime;

    /**
     * Whether this NPC will auto-attack eligible players.
     */
    private final boolean aggressive;

    /**
     * Whether this NPC can apply poison on hit (mechanics defined elsewhere).
     */
    private final boolean poisonous;

    /**
     * The combat level displayed for this NPC.
     */
    private final int level;

    /**
     * The NPC hitpoints (life points) value.
     */
    private final int hitpoints;

    /**
     * The maximum melee hit (or general max hit if you use a unified model).
     */
    private final int maximumHit;

    /**
     * Attack delay in ticks between attacks.
     */
    private final int attackSpeed;

    /**
     * Animation id played when this NPC attacks.
     */
    private final int attackAnimation;

    /**
     * Animation id played when this NPC blocks/defends.
     */
    private final int defenceAnimation;

    /**
     * Animation id played when this NPC dies.
     */
    private final int deathAnimation;

    /**
     * Combat “skill levels” array.
     * <p>
     * Expected ordering is defined by the skill index constants:
     * {@link #ATTACK}, {@link #STRENGTH}, {@link #DEFENCE}, {@link #RANGED}, {@link #MAGIC}.
     */
    private final int[] skills;

    /**
     * Combat bonuses array.
     * <p>
     * Expected ordering is defined by the bonus index constants (attack then defence):
     * {@link #STAB_ATTACK}..{@link #RANGED_DEFENCE}.
     */
    private final int[] bonuses;

    /**
     * Creates a new {@link NpcCombatDefinition}.
     *
     * @param id The NPC id this definition belongs to.
     * @param respawnTime Respawn delay in ticks.
     * @param aggressive Whether the NPC is aggressive.
     * @param poisonous Whether the NPC can poison targets.
     * @param level The combat level.
     * @param hitpoints The hitpoints value.
     * @param maximumHit The maximum hit value.
     * @param attackSpeed Attack delay in ticks.
     * @param attackAnimation Attack animation id.
     * @param defenceAnimation Defence/block animation id.
     * @param deathAnimation Death animation id.
     * @param skills Skill level array (defensively copied).
     * @param bonuses Bonus array (defensively copied).
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

    /**
     * Returns the NPC id this combat definition belongs to.
     *
     * @return The NPC id.
     */
    @Override
    public int getId() {
        return id;
    }

    /**
     * Returns the respawn delay in ticks.
     *
     * @return The respawn delay.
     */
    public int getRespawnTime() {
        return respawnTime;
    }

    /**
     * Returns whether this NPC is aggressive.
     *
     * @return {@code true} if aggressive.
     */
    public boolean isAggressive() {
        return aggressive;
    }

    /**
     * Returns whether this NPC can poison targets.
     *
     * @return {@code true} if poisonous.
     */
    public boolean isPoisonous() {
        return poisonous;
    }

    /**
     * Returns the combat level.
     *
     * @return The combat level.
     */
    public int getLevel() {
        return level;
    }

    /**
     * Returns the hitpoints value.
     *
     * @return The hitpoints.
     */
    public int getHitpoints() {
        return hitpoints;
    }

    /**
     * Returns the maximum hit value.
     *
     * @return The maximum hit.
     */
    public int getMaximumHit() {
        return maximumHit;
    }

    /**
     * Returns the attack speed in ticks.
     *
     * @return The attack speed.
     */
    public int getAttackSpeed() {
        return attackSpeed;
    }

    /**
     * Returns the attack animation id.
     *
     * @return The attack animation id.
     */
    public int getAttackAnimation() {
        return attackAnimation;
    }

    /**
     * Returns the defence animation id.
     *
     * @return The defence animation id.
     */
    public int getDefenceAnimation() {
        return defenceAnimation;
    }

    /**
     * Returns the death animation id.
     *
     * @return The death animation id.
     */
    public int getDeathAnimation() {
        return deathAnimation;
    }

    /**
     * Returns the skill level at the given skill index.
     * <p>
     * Callers should use the provided skill index constants ({@link #ATTACK}, {@link #STRENGTH}, etc.) to avoid
     * hardcoding indices.
     * <p>
     * Note: This method will throw {@link ArrayIndexOutOfBoundsException} if an invalid index is used.
     *
     * @param id The skill index.
     * @return The skill level value.
     */
    public int getSkill(int id) {
        return skills[id];
    }
}
