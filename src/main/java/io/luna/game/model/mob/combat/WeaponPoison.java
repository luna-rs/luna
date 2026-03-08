package io.luna.game.model.mob.combat;

/**
 * Enumerates the supported weapon poison variants that can be applied during combat.
 *
 * @author lare96
 */
public enum WeaponPoison {

    /**
     * Standard poison delivered by a melee weapon.
     */
    MELEE,

    /**
     * Standard poison delivered by a ranged weapon.
     */
    RANGED,

    /**
     * Strong poison delivered by a melee weapon.
     */
    MELEE_PLUS,

    /**
     * Strong poison delivered by a ranged weapon.
     */
    RANGED_PLUS,

    /**
     * Strongest poison delivered by a melee weapon.
     */
    MELEE_PLUS_PLUS,

    /**
     * Strongest poison delivered by a ranged weapon.
     */
    RANGED_PLUS_PLUS
}