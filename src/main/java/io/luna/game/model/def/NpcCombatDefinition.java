package io.luna.game.model.def;

import io.luna.game.model.item.Equipment.EquipmentBonus;
import io.luna.game.model.mob.Npc;

import java.util.Arrays;

/**
 * A definition describing combat-related data for an attackable {@link Npc}.
 *
 * @author lare96
 */
public final class NpcCombatDefinition implements Definition {

    /* -------- Skill index constants -------- */

    /**
     * Skill array index for Attack.
     */
    public static final int ATTACK = 0;

    /**
     * Skill array index for Strength.
     */
    public static final int STRENGTH = 1;

    /**
     * Skill array index for Defence.
     */
    public static final int DEFENCE = 2;

    /**
     * Skill array index for Ranged.
     */
    public static final int RANGED = 3;

    /**
     * Skill array index for Magic.
     */
    public static final int MAGIC = 4;

    /**
     * Repository of {@link NpcCombatDefinition}s keyed by NPC id.
     */
    public static final DefinitionRepository<NpcCombatDefinition> ALL = new MapDefinitionRepository<>();

    /**
     * The NPC id this combat definition belongs to.
     */
    private final int id;

    /**
     * Respawn delay, in ticks, after this NPC dies.
     */
    private final int respawnTime;

    /**
     * Whether this NPC will automatically attack eligible players.
     */
    private final boolean aggressive;

    /**
     * Whether this NPC can apply poison on hit.
     * <p>
     * Poison mechanics and severity are defined elsewhere.
     */
    private final boolean poisonous;

    /**
     * The combat level displayed for this NPC.
     */
    private final int level;

    /**
     * The NPC's hitpoints value.
     */
    private final int hitpoints;

    /**
     * The maximum hit this NPC can deal.
     */
    private final int maximumHit;

    /**
     * Attack delay, in ticks, between consecutive attacks.
     */
    private final int attackSpeed;

    /**
     * Animation id played when this NPC attacks.
     */
    private final int attackAnimation;

    /**
     * Animation id played when this NPC blocks or defends.
     */
    private final int defenceAnimation;

    /**
     * Animation id played when this NPC dies.
     */
    private final int deathAnimation;

    /**
     * Combat skill level array.
     * <p>
     * Expected ordering is defined by the skill index constants:
     * {@link #ATTACK}, {@link #STRENGTH}, {@link #DEFENCE}, {@link #RANGED}, and {@link #MAGIC}.
     */
    private final int[] skills;

    /**
     * Combat bonus array.
     * <p>
     * Expected ordering matches {@link EquipmentBonus} index order.
     */
    private final int[] bonuses;

    /**
     * The default attack bonus/type for this NPC.
     * <p>
     * This is used as the preferred attack style classification when the NPC's combat logic needs a default attack
     * bonus rather than deriving one dynamically from its bonus array.
     */
    private final EquipmentBonus defaultAttackType;

    /**
     * Creates a new {@link NpcCombatDefinition}.
     *
     * @param id The NPC id this definition belongs to.
     * @param respawnTime The respawn delay in ticks.
     * @param aggressive Whether the NPC is aggressive.
     * @param poisonous Whether the NPC can poison targets.
     * @param level The displayed combat level.
     * @param hitpoints The hitpoints value.
     * @param maximumHit The maximum hit value.
     * @param attackSpeed The attack delay in ticks.
     * @param attackAnimation The attack animation id.
     * @param defenceAnimation The defence/block animation id.
     * @param deathAnimation The death animation id.
     * @param skills The combat skill level array; defensively copied.
     * @param bonuses The combat bonus array; defensively copied.
     * @param defaultAttackType The default attack bonus/type for this NPC.
     */
    public NpcCombatDefinition(int id, int respawnTime, boolean aggressive, boolean poisonous, int level,
                               int hitpoints, int maximumHit, int attackSpeed, int attackAnimation, int defenceAnimation,
                               int deathAnimation, int[] skills, int[] bonuses, EquipmentBonus defaultAttackType) {
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
        this.defaultAttackType = defaultAttackType;
    }

    @Override
    public int getId() {
        return id;
    }

    /**
     * @return The respawn delay.
     */
    public int getRespawnTime() {
        return respawnTime;
    }

    /**
     * @return {@code true} if this NPC auto-attacks eligible targets.
     */
    public boolean isAggressive() {
        return aggressive;
    }

    /**
     * @return {@code true} if this NPC can apply poison.
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
     * @return The hitpoints value.
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
     * @return The attack animation id.
     */
    public int getAttackAnimation() {
        return attackAnimation;
    }

    /**
     * @return The defence animation id.
     */
    public int getDefenceAnimation() {
        return defenceAnimation;
    }

    /**
     * @return The death animation id.
     */
    public int getDeathAnimation() {
        return deathAnimation;
    }

    /**
     * Returns the skill level at the given skill index.
     * <p>
     * Callers should use the provided skill index constants such as {@link #ATTACK} and {@link #MAGIC} rather than
     * hardcoding raw indices.
     *
     * @param id The skill index.
     * @return The skill level value at that index.
     * @throws ArrayIndexOutOfBoundsException If the supplied index is invalid.
     */
    public int getSkill(int id) {
        return skills[id];
    }

    /**
     * Returns the combat bonus value for the given bonus type.
     *
     * @param bonus The combat bonus to look up.
     * @return The stored bonus value.
     */
    public int getBonus(EquipmentBonus bonus) {
        return bonuses[bonus.getIndex()];
    }

    /**
     * Finds the highest attack bonus present in this NPC's bonus array.
     * <p>
     * If no positive bonus is found, this falls back to {@link EquipmentBonus#STAB_ATTACK}.
     *
     * @return The highest attack bonus type, or {@link EquipmentBonus#STAB_ATTACK} as a fallback.
     */
    public EquipmentBonus findHighestAttackBonus() {
        int lastIndex = -1;
        int lastBonus = 0;
        for (int index = 0; index < bonuses.length; index++) {
            int amount = bonuses[index];
            if (amount > lastBonus) {
                lastBonus = amount;
                lastIndex = index;
            }
        }
        return lastIndex == -1 ? EquipmentBonus.STAB_ATTACK : EquipmentBonus.forIndex(lastIndex);
    }

    /**
     * @return The default attack bonus/type.
     */
    public EquipmentBonus getDefaultAttackBonus() {
        return defaultAttackType;
    }
}