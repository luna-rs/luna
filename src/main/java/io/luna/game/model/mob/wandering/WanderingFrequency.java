package io.luna.game.model.mob.wandering;

import io.luna.util.Rational;

/**
 * Represents how frequently a wandering action should attempt to move a mob.
 * <p>
 * Each frequency value is backed by a {@link Rational} chance that can be used with random roll utilities (for
 * example {@code RandomUtils.roll(Rational)}). Higher frequencies correspond to higher chances of attempting movement
 * on a given tick.
 *
 * @author lare96
 */
public enum WanderingFrequency {

    /**
     * Low chance of attempting to wander.
     */
    SLOW(Rational.UNCOMMON),

    /**
     * Default chance of attempting to wander.
     */
    NORMAL(Rational.COMMON),

    /**
     * High chance of attempting to wander.
     */
    FAST(Rational.VERY_COMMON),

    /**
     * Always attempts to wander when conditions allow.
     */
    RAPID(Rational.ALWAYS);

    /**
     * The underlying chance used to decide whether a wander attempt occurs.
     */
    private final Rational chance;

    /**
     * Creates a new wandering frequency with the given chance.
     *
     * @param chance The chance associated with this frequency.
     */
    WanderingFrequency(Rational chance) {
        this.chance = chance;
    }

    /**
     * Returns the chance associated with this wandering frequency.
     *
     * @return The {@link Rational} chance value.
     */
    public Rational getChance() {
        return chance;
    }
}
