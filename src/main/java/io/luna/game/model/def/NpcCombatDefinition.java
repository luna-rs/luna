package io.luna.game.model.def;

import io.luna.game.model.item.Equipment.EquipmentBonus;
import io.luna.game.model.mob.Npc;
import io.luna.game.model.mob.NpcAggressionProfile;

import java.util.Arrays;

/**
 * A definition containing all combat-related data for an attackable {@link Npc}.
 * <p>
 * This definition stores combat stats, offensive and defensive bonuses, animations, respawn timing, poison flags, and
 * aggression behavior used by the combat system.
 *
 * @author lare96
 */
public final class NpcCombatDefinition implements Definition {

    /**
     * Repository of {@link NpcCombatDefinition} instances keyed by NPC id.
     */
    public static final DefinitionRepository<NpcCombatDefinition> ALL = new MapDefinitionRepository<>();

    /**
     * The NPC id this combat definition belongs to.
     */
    private final int id;

    /**
     * The respawn delay, in ticks, after this NPC dies.
     */
    private final int respawnTime;

    /**
     * The aggression profile that determines when this NPC will automatically attack players.
     * <p>
     * A {@code null} value means this NPC has no aggression profile configured.
     */
    private final NpcAggressionProfile aggression;

    /**
     * Whether this NPC can inflict poison on successful hits.
     * <p>
     * Poison severity and application behavior are handled elsewhere.
     */
    private final boolean poisonous;

    /**
     * Whether this NPC is immune to poison.
     */
    private final boolean immunePoison;

    /**
     * The NPC's hitpoints value.
     */
    private final int hitpoints;

    /**
     * The maximum damage this NPC can deal with a single hit.
     */
    private final int maximumHit;

    /**
     * The delay, in ticks, between this NPC's attacks.
     */
    private final int attackSpeed;

    /**
     * The animation id played when this NPC attacks.
     */
    private final int attackAnimation;

    /**
     * The animation id played when this NPC blocks or defends.
     */
    private final int defenceAnimation;

    /**
     * The animation id played when this NPC dies.
     */
    private final int deathAnimation;

    /**
     * The NPC's generic attack bonus value.
     */
    private final int attackBonus;

    /**
     * The NPC's magic attack bonus value.
     */
    private final int magicBonus;

    /**
     * The NPC's ranged attack bonus value.
     */
    private final int rangedBonus;

    /**
     * Combat skill levels for this NPC.
     * <p>
     * Expected ordering:
     * <ul>
     *     <li>index 0 = Attack</li>
     *     <li>index 1 = Strength</li>
     *     <li>index 2 = Defence</li>
     *     <li>index 3 = Ranged</li>
     *     <li>index 4 = Magic</li>
     * </ul>
     */
    private final int[] skills;

    /**
     * Defensive and strength bonuses for this NPC.
     * <p>
     * Expected ordering:
     * <ul>
     *     <li>index 0 = Stab defence</li>
     *     <li>index 1 = Slash defence</li>
     *     <li>index 2 = Crush defence</li>
     *     <li>index 3 = Magic defence</li>
     *     <li>index 4 = Ranged defence</li>
     *     <li>index 5 = Strength bonus</li>
     * </ul>
     */
    private final int[] bonuses;

    /**
     * Creates a new {@link NpcCombatDefinition}.
     *
     * @param id The NPC id this definition belongs to.
     * @param respawnTime The respawn delay, in ticks.
     * @param aggression The aggression profile for this NPC, or {@code null} if none is configured.
     * @param poisonous Whether this NPC can poison targets.
     * @param immunePoison Whether this NPC is immune to poison.
     * @param hitpoints The NPC's hitpoints value.
     * @param maximumHit The maximum damage this NPC can deal.
     * @param attackSpeed The delay, in ticks, between attacks.
     * @param attackAnimation The attack animation id.
     * @param defenceAnimation The defence animation id.
     * @param deathAnimation The death animation id.
     * @param attackBonus The generic attack bonus value.
     * @param magicBonus The magic attack bonus value.
     * @param rangedBonus The ranged attack bonus value.
     * @param skills The combat skill level array. This array is defensively copied.
     * @param bonuses The defensive and strength bonus array. This array is defensively copied.
     */
    public NpcCombatDefinition(
            int id,
            int respawnTime,
            NpcAggressionProfile aggression,
            boolean poisonous,
            boolean immunePoison,
            int hitpoints,
            int maximumHit,
            int attackSpeed,
            int attackAnimation,
            int defenceAnimation,
            int deathAnimation,
            int attackBonus,
            int magicBonus,
            int rangedBonus,
            int[] skills,
            int[] bonuses
    ) {
        this.id = id;
        this.respawnTime = respawnTime;
        this.aggression = aggression;
        this.poisonous = poisonous;
        this.immunePoison = immunePoison;
        this.hitpoints = hitpoints;
        this.maximumHit = maximumHit;
        this.attackSpeed = attackSpeed;
        this.attackAnimation = attackAnimation;
        this.defenceAnimation = defenceAnimation;
        this.deathAnimation = deathAnimation;
        this.attackBonus = attackBonus;
        this.magicBonus = magicBonus;
        this.rangedBonus = rangedBonus;
        this.skills = Arrays.copyOf(skills, skills.length);
        this.bonuses = Arrays.copyOf(bonuses, bonuses.length);
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
     * @return {@code true} if this NPC can poison targets.
     */
    public boolean isPoisonous() {
        return poisonous;
    }

    /**
     * @return {@code true} if this NPC cannot be poisoned.
     */
    public boolean isImmunePoison() {
        return immunePoison;
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
     * @return The attack bonus.
     */
    public int getAttackBonus() {
        return attackBonus;
    }

    /**
     * @return The magic bonus.
     */
    public int getMagicBonus() {
        return magicBonus;
    }

    /**
     * @return The ranged bonus.
     */
    public int getRangedBonus() {
        return rangedBonus;
    }

    /**
     * @return The aggression profile, or {@code null} if none is configured.
     */
    public NpcAggressionProfile getAggression() {
        return aggression;
    }

    /**
     * @return The Attack level.
     */
    public int getAttackLevel() {
        return skills[0];
    }

    /**
     * @return The Strength level.
     */
    public int getStrengthLevel() {
        return skills[1];
    }

    /**
     * @return The Defence level.
     */
    public int getDefenceLevel() {
        return skills[2];
    }

    /**
     * @return The Ranged level.
     */
    public int getRangedLevel() {
        return skills[3];
    }

    /**
     * @return The Magic level.
     */
    public int getMagicLevel() {
        return skills[4];
    }

    /**
     * @return The stab defence bonus.
     */
    public int getStabDefenceBonus() {
        return bonuses[0];
    }

    /**
     * @return The slash defence bonus.
     */
    public int getSlashDefenceBonus() {
        return bonuses[1];
    }

    /**
     * @return The crush defence bonus.
     */
    public int getCrushDefenceBonus() {
        return bonuses[2];
    }

    /**
     * @return The magic defence bonus.
     */
    public int getMagicDefenceBonus() {
        return bonuses[3];
    }

    /**
     * @return The ranged defence bonus.
     */
    public int getRangedDefenceBonus() {
        return bonuses[4];
    }

    /**
     * @return The strength bonus.
     */
    public int getStrengthBonus() {
        return bonuses[5];
    }

    /**
     * Returns the defence bonus that opposes the given attack-style bonus.
     * <p>
     * This maps an incoming attack bonus type to its corresponding defensive bonus. For example,
     * {@link EquipmentBonus#STAB_ATTACK} maps to stab defence, and {@link EquipmentBonus#MAGIC_ATTACK}
     * maps to magic defence.
     *
     * @param attackStyleBonus The attack-style bonus to resolve against.
     * @return The matching defence bonus value for the supplied attack style.
     * @throws IllegalArgumentException If the supplied bonus is not a supported attack-style bonus.
     */
    public int getMatchingDefenceBonus(EquipmentBonus attackStyleBonus) {
        switch (attackStyleBonus) {
            case STAB_ATTACK:
                return getStabDefenceBonus();
            case SLASH_ATTACK:
                return getSlashDefenceBonus();
            case CRUSH_ATTACK:
                return getCrushDefenceBonus();
            case MAGIC_ATTACK:
                return getMagicDefenceBonus();
            case RANGED_ATTACK:
                return getRangedDefenceBonus();
            default:
                throw new IllegalArgumentException("Invalid attack style bonus: " + attackStyleBonus);
        }
    }
}