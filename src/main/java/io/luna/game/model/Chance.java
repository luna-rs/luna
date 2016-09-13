package io.luna.game.model;

import io.luna.util.Rational;

/**
 * A model that contains functions related to chance.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class Chance {

    /**
     * Returns {@code str} as a rational number related to chance. This function is <strong>not</strong> a
     * general purpose parsing method for rational numbers.
     */
    public static Rational parseChance(String str) {
        switch (str) {
        case "ALWAYS":
            return ALWAYS;
        case "VERY_COMMON":
            return VERY_COMMON;
        case "COMMON":
            return COMMON;
        case "UNCOMMON":
            return UNCOMMON;
        case "VERY_UNCOMMON":
            return VERY_UNCOMMON;
        case "RARE":
            return RARE;
        case "VERY_RARE":
            return VERY_RARE;
        default:
            String[] rational = str.split("/");
            int n = Integer.parseInt(rational[0]);
            int d = Integer.parseInt(rational[1]);
            return new Rational(n, d);
        }
    }

    /**
     * A rational number describing an {@code ALWAYS} chance (100%, 1 in 1 chance).
     */
    public static final Rational ALWAYS = new Rational(1, 1);

    /**
     * A rational number describing a {@code VERY_COMMON} chance (50%, 1 in 2 chance).
     */
    public static final Rational VERY_COMMON = new Rational(1, 2);

    /**
     * A rational number describing an {@code COMMON} chance (25%, 1 in 4 chance).
     */
    public static final Rational COMMON = new Rational(1, 4);

    /**
     * A rational number describing an {@code UNCOMMON} chance (10%, 1 in 10 chance).
     */
    public static final Rational UNCOMMON = new Rational(1, 10);

    /**
     * A rational number describing an {@code VERY_UNCOMMON} chance (2.5%, 5 in 200 chance).
     */
    public static final Rational VERY_UNCOMMON = new Rational(5, 200);

    /**
     * A rational number describing an {@code RARE} chance (0.7%, 1 in 150 chance).
     */
    public static final Rational RARE = new Rational(1, 150);

    /**
     * A rational number describing an {@code VERY_RARE} chance (0.3%, 1 in 300 chance).
     */
    public static final Rational VERY_RARE = new Rational(1, 300);

    /**
     * A private constructor.
     */
    private Chance() {
    }
}
