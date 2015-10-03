package io.luna.game.model.def;

import io.luna.game.model.mobile.Npc;

/**
 * A cached definition that describes a specific {@link Npc}.
 * 
 * @author lare96 <http://github.org/lare96>
 */
public final class NpcDefinition {

    /**
     * An array of the cached {@link NpcDefinition}s.
     */
    public static final NpcDefinition[] DEFINITIONS = new NpcDefinition[8152];

    /**
     * The identification for the {@code Npc}.
     */
    private final int id;

    /**
     * The name of the {@code Npc}.
     */
    private final String name;

    /**
     * The description of the {@code Npc}.
     */
    private final String description;

    /**
     * The combat level of the {@code Npc}.
     */
    private final int combatLevel;

    /**
     * The size of the {@code Npc}.
     */
    private final int size;

    /**
     * Determines if the {@code Npc} can be attacked.
     */
    private final boolean attackable;

    /**
     * Determines if the {@code Npc} is aggressive.
     */
    private final boolean aggressive;

    /**
     * Determines if the {@code Npc} retreats.
     */
    private final boolean retreats;

    /**
     * Determines if the {@code Npc} is poisonous.
     */
    private final boolean poisonous;

    /**
     * The time it takes for the {@code Npc} to respawn.
     */
    private final int respawnTime;

    /**
     * The max hit of the {@code Npc}.
     */
    private final int maxHit;

    /**
     * The maximum amount of hitpoints the {@code Npc} has.
     */
    private final int hitpoints;

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
     * The attack bonus of the {@code Npc}.
     */
    private final int attackBonus;

    /**
     * The melee defence bonus of the {@code Npc}.
     */
    private final int meleeDefence;

    /**
     * The ranged defence of the {@code Npc}.
     */
    private final int rangedDefence;

    /**
     * The magic defence of the {@code Npc}.
     */
    private final int magicDefence;

    /**
     * Creates a new {@link NpcDefintion}.
     *
     * @param id The identification for the {@code Npc}.
     * @param name The name of the {@code Npc}.
     * @param description The description of the {@code Npc}.
     * @param combatLevel The combat level of the {@code Npc}.
     * @param size The size of the {@code Npc}.
     * @param attackable Determines if the {@code Npc} can be attacked.
     * @param aggressive Determines if the {@code Npc} is aggressive.
     * @param retreats Determines if the {@code Npc} retreats.
     * @param poisonous Determines if the {@code Npc} is poisonous.
     * @param respawnTime The time it takes for the {@code Npc} to respawn.
     * @param maxHit The max hit of the {@code Npc}.
     * @param hitpoints The maximum amount of hitpoints the {@code Npc} has.
     * @param attackSpeed The attack speed of the {@code Npc}.
     * @param attackAnimation The attack animation of the {@code Npc}.
     * @param defenceAnimation The defence animation of the {@code Npc}.
     * @param deathAnimation The death animation of the {@code Npc}.
     * @param attackBonus The attack bonus of the {@code Npc}.
     * @param meleeDefence The melee defence bonus of the {@code Npc}.
     * @param rangedDefence The ranged defence of the {@code Npc}.
     * @param magicDefence The magic defence of the {@code Npc}.
     */
    public NpcDefinition(int id, String name, String description, int combatLevel, int size, boolean attackable, boolean aggressive, boolean retreats, boolean poisonous,
        int respawnTime, int maxHit, int hitpoints, int attackSpeed, int attackAnimation, int defenceAnimation, int deathAnimation, int attackBonus, int meleeDefence,
        int rangedDefence, int magicDefence) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.combatLevel = combatLevel;
        this.size = size;
        this.attackable = attackable;
        this.aggressive = aggressive;
        this.retreats = retreats;
        this.poisonous = poisonous;
        this.respawnTime = respawnTime;
        this.maxHit = maxHit;
        this.hitpoints = hitpoints;
        this.attackSpeed = attackSpeed;
        this.attackAnimation = attackAnimation;
        this.defenceAnimation = defenceAnimation;
        this.deathAnimation = deathAnimation;
        this.attackBonus = attackBonus;
        this.meleeDefence = meleeDefence;
        this.rangedDefence = rangedDefence;
        this.magicDefence = magicDefence;
    }

    /**
     * @return The identification of the {@code Npc}.
     */
    public int getId() {
        return id;
    }

    /**
     * @return The name of the {@code Npc}.
     */
    public String getName() {
        return name;
    }

    /**
     * @return The description of the {@code Npc}.
     */
    public String getDescription() {
        return description;
    }

    /**
     * @return The description of the {@code Npc}.
     */
    public int getCombatLevel() {
        return combatLevel;
    }

    /**
     * @return The size of the {@code Npc}.
     */
    public int getSize() {
        return size;
    }

    /**
     * @return {@code true} if the {@code Npc} can be attacked, {@code false}
     *         otherwise.
     */
    public boolean isAttackable() {
        return attackable;
    }

    /**
     * @return {@code true} if the {@code Npc} is aggressive, {@code false}
     *         otherwise.
     */
    public boolean isAggressive() {
        return aggressive;
    }

    /**
     * @return {@code true} if the {@code Npc} can retreat, {@code false}
     *         otherwise.
     */
    public boolean isRetreats() {
        return retreats;
    }

    /**
     * @return {@code true} if the {@code Npc} is poisonous, {@code false}
     *         otherwise.
     */
    public boolean isPoisonous() {
        return poisonous;
    }

    /**
     * @return The time it takes for the {@code Npc} to respawn.
     */
    public int getRespawnTime() {
        return ((respawnTime - 1) <= 0 ? 1 : (respawnTime - 1));
    }

    /**
     * @return The max hit of the {@code Npc}.
     */
    public int getMaxHit() {
        return maxHit;
    }

    /**
     * @return The maximum amount of hitpoints the {@code Npc} has.
     */
    public int getHitpoints() {
        return hitpoints;
    }

    /**
     * @return The maximum amount of hitpoints the {@code Npc} has.
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
     * @return The attack bonus of the {@code Npc}.
     */
    public int getAttackBonus() {
        return attackBonus;
    }

    /**
     * @return The melee defence bonus of the {@code Npc}.
     */
    public int getMeleeDefence() {
        return meleeDefence;
    }

    /**
     * @return The ranged defence of the {@code Npc}.
     */
    public int getRangedDefence() {
        return rangedDefence;
    }

    /**
     * @return The magic defence of the {@code Npc}.
     */
    public int getMagicDefence() {
        return magicDefence;
    }
}
