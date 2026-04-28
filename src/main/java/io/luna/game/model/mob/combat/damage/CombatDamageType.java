package io.luna.game.model.mob.combat.damage;

/**
 * The primary combat damage categories used by the combat system.
 * <p>
 * Damage types are used to distinguish between melee, magic, and ranged combat interactions.
 *
 * @author lare96
 */
public enum CombatDamageType {

    /**
     * Damage dealt through melee combat.
     */
    MELEE,

    /**
     * Damage dealt through magic combat.
     */
    MAGIC,

    /**
     * Damage dealt through ranged combat.
     */
    RANGED
}