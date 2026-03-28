package io.luna.game.model.mob.combat;

/**
 * The combat stance used by a selected {@link CombatStyle}.
 * <p>
 * Combat stances determine the behavioral mode of an {@link CombatStyle}, such as whether it emphasizes accuracy,
 * strength, defence, balanced training, or spell autocasting.
 *
 * @author lare96
 */
public enum CombatStance {

    /**
     * A stance focused on improving hit accuracy.
     */
    ACCURATE,

    /**
     * A stance focused on maximizing damage output.
     */
    AGGRESSIVE,

    /**
     * A stance that balances training across accurate, aggressive, and defensive styles.
     */
    CONTROLLED,

    /**
     * A stance focused on defensive combat training.
     */
    DEFENSIVE
}