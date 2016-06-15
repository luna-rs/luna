package io.luna.game.model.def;

import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Ints;
import io.luna.game.model.mobile.Npc;

import java.util.HashMap;
import java.util.Map;

/**
 * A cached definition that describes specific combat properties for {@link Npc}s.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class NpcCombatDefinition {

    /**
     * The attack skill index in the {@code skills} list.
     */
    public static final int ATTACK = 0;

    /**
     * The strength skill index in the {@code skills} list.
     */
    public static final int STRENGTH = 1;

    /**
     * The defence skill index in the {@code skills} list.
     */
    public static final int DEFENCE = 2;

    /**
     * The ranged skill index in the {@code skills} list.
     */
    public static final int RANGED = 3;

    /**
     * The magic skill index in the {@code skills} list.
     */
    public static final int MAGIC = 4;

    /**
     * The stab attack bonus in the {@code bonuses} list.
     */
    public static final int STAB_ATTACK = 0;

    /**
     * The slash attack bonus in the {@code bonuses} list.
     */
    public static final int SLASH_ATTACK = 1;

    /**
     * The crush attack bonus in the {@code bonuses} list.
     */
    public static final int CRUSH_ATTACK = 2;

    /**
     * The magic attack bonus in the {@code bonuses} list.
     */
    public static final int MAGIC_ATTACK = 3;

    /**
     * The ranged attack bonus in the {@code bonuses} list.
     */
    public static final int RANGED_ATTACK = 4;

    /**
     * The stab defence bonus in the {@code bonuses} list.
     */
    public static final int STAB_DEFENCE = 5;

    /**
     * The slash defence bonus in the {@code bonuses} list.
     */
    public static final int SLASH_DEFENCE = 6;

    /**
     * The crush defence bonus in the {@code bonuses} list.
     */
    public static final int CRUSH_DEFENCE = 7;

    /**
     * The magic defence bonus in the {@code bonuses} list.
     */
    public static final int MAGIC_DEFENCE = 8;

    /**
     * The ranged defence bonus in the {@code bonuses} list.
     */
    public static final int RANGED_DEFENCE = 9;

    /**
     * A {@link Map} of the cached {@code NpcCombatDefinition}s.
     */
    public static final Map<Integer, NpcCombatDefinition> DEFINITIONS = new HashMap<>();

    /**
     * The default {@link NpcCombatDefinition} used when none in {@code DEFINITIONS} can be assigned to an {@code Npc}.
     */
    public static final NpcCombatDefinition DEFAULT = new NpcCombatDefinition(-1, 8, false, false, -1, -1, -1, -1, -1, -1,
        -1, new int[5], new int[10]);

    /**
     * Retrieves a cached {@link NpcCombatDefinition} by its {@code id}.
     *
     * @param id The identifier to retrieve the {@code NpcCombatDefinition}.
     * @return The {@code NpcCombatDefinition} instance.
     */
    public static NpcCombatDefinition getDefinition(int id) {
        return DEFINITIONS.getOrDefault(id, DEFAULT);
    }

    /**
     * The id of the {@code Npc} this definition is for.
     */
    private final int id;

    /**
     * The amount of ticks it takes to respawn.
     */
    private final int respawnTicks;

    /**
     * If the {@code Npc} is aggressive.
     */
    private final boolean aggressive;

    /**
     * If the {@code Npc} is poisonous.
     */
    private final boolean poisonous;

    /**
     * The combat level of the {@code Npc}.
     */
    private final int combatLevel;

    /**
     * The hitpoint amount of the {@code Npc}.
     */
    private final int hitpoints;

    /**
     * The maximum hit of the {@code Npc}.
     */
    private final int maximumHit;

    /**
     * The attack speed of the {@code Npc}.
     */
    private final int attackSpeed;

    /**
     * The attack animation of the {@code Npc}.
     */
    private final int attackAnimation;

    /**
     * The defence animation of the {@code Npc}.
     */
    private final int defenceAnimation;

    /**
     * The death animation of the {@code Npc}.
     */
    private final int deathAnimation;

    /**
     * The skills (attack, strength, defence, ranged, magic) of the {@code Npc}.
     */
    private final ImmutableList<Integer> skills;

    /**
     * The bonuses (stab atk, slash atk, crush atk, magic atk, ranged atk, stab def, slash def, crush def, magic def, ranged
     * def) of the {@code Npc}.
     */
    private final ImmutableList<Integer> bonuses;

    /**
     * @param id The id of the {@code Npc} this definition is for.
     * @param respawnTicks The amount of ticks it takes to respawn.
     * @param aggressive If the {@code Npc} is aggressive.
     * @param poisonous If the {@code Npc} is poisonous.
     * @param combatLevel The combat level of the {@code Npc}.
     * @param hitpoints The hitpoint amount of the {@code Npc}.
     * @param maximumHit The maximum hit of the {@code Npc}.
     * @param attackSpeed The attack speed of the {@code Npc}.
     * @param attackAnimation The attack animation of the {@code Npc}.
     * @param defenceAnimation The defence animation of the {@code Npc}.
     * @param deathAnimation The death animation of the {@code Npc}.
     * @param skills The skills (attack, strength, defence, ranged, magic) of the {@code Npc}.
     * @param bonuses The bonuses (stab atk, slash atk, crush atk, magic atk, ranged atk, stab def, slash def, crush def,
     * magic def, ranged def) of the {@code Npc}.
     */
    public NpcCombatDefinition(int id, int respawnTicks, boolean aggressive, boolean poisonous, int combatLevel,
        int hitpoints, int maximumHit, int attackSpeed, int attackAnimation, int defenceAnimation, int deathAnimation,
        int[] skills, int[] bonuses) {
        this.id = id;
        this.respawnTicks = respawnTicks;
        this.aggressive = aggressive;
        this.poisonous = poisonous;
        this.combatLevel = combatLevel;
        this.hitpoints = hitpoints;
        this.maximumHit = maximumHit;
        this.attackSpeed = attackSpeed;
        this.attackAnimation = attackAnimation;
        this.defenceAnimation = defenceAnimation;
        this.deathAnimation = deathAnimation;
        this.skills = ImmutableList.copyOf(Ints.asList(skills));
        this.bonuses = ImmutableList.copyOf(Ints.asList(bonuses));
    }

    /**
     * @return The id of the {@code Npc} this definition is for.
     */
    public int getId() {
        return id;
    }

    /**
     * @return The amount of ticks it takes to respawn.
     */
    public int getRespawnTicks() {
        return respawnTicks;
    }

    /**
     * @return {@code true} if the {@code Npc} is aggressive, {@code false} otherwise.
     */
    public boolean isAggressive() {
        return aggressive;
    }

    /**
     * @return {@code true} if the {@code Npc} is poisonous, {@code false} otherwise.
     */
    public boolean isPoisonous() {
        return poisonous;
    }

    /**
     * @return The combat level of the {@code Npc}.
     */
    public int getCombatLevel() {
        return combatLevel;
    }

    /**
     * @return The hitpoint amount of the {@code Npc}.
     */
    public int getHitpoints() {
        return hitpoints;
    }

    /**
     * @return The maximum hit of the {@code Npc}.
     */
    public int getMaximumHit() {
        return maximumHit;
    }

    /**
     * @return The attack speed of the {@code Npc}.
     */
    public int getAttackSpeed() {
        return attackSpeed;
    }

    /**
     * @return The attack animation of the {@code Npc}.
     */
    public int getAttackAnimation() {
        return attackAnimation;
    }

    /**
     * @return The defence animation of the {@code Npc}.
     */
    public int getDefenceAnimation() {
        return defenceAnimation;
    }

    /**
     * @return The death animation of the {@code Npc}.
     */
    public int getDeathAnimation() {
        return deathAnimation;
    }

    /**
     * @return The skills (attack, strength, defence, ranged, magic) of the {@code Npc}.
     */
    public ImmutableList<Integer> getSkills() {
        return skills;
    }

    /**
     * @return The bonuses (stab atk, slash atk, crush atk, magic atk, ranged atk, stab def, slash def, crush def, magic def,
     * ranged def) of the {@code Npc}.
     */
    public ImmutableList<Integer> getBonuses() {
        return bonuses;
    }
}
