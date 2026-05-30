package io.luna.util;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * Utility methods for formatting and comparing numeric values.
 * <p>
 * This class is mainly used for game-facing numbers such as item prices, trade values, and tolerance checks.
 *
 * @author lare96
 */
public class NumberUtils {

    /**
     * A whole-number formatter using Canadian grouping rules.
     */
    private static final NumberFormat WHOLE_NUMBER = NumberFormat.getIntegerInstance(Locale.CANADA);

    /**
     * Formats abbreviated numbers with up to one decimal place.
     */
    private static final DecimalFormat ABBREVIATED = new DecimalFormat("0.#");

    /**
     * Formats a GP value using compact RuneScape-style suffixes.
     * <p>
     * Values below {@code 10,000} are shown as their raw integer value followed by {@code gp}. Values at or above
     * {@code 10,000} are abbreviated using {@code K} or {@code M}. Abbreviated values are rounded up so the displayed
     * price does not understate the real value.
     *
     * @param value The GP value to format.
     * @return The formatted GP value.
     */
    public static String formatPrice(int value) {
        if (value < 10_000) {
            return value + "gp";
        }
        if (value < 1_000_000) {
            return abbreviateCeil(value, 1_000, "K", 1);
        }
        return abbreviateCeil(value, 1_000_000, "M", 3);
    }

    /**
     * Returns whether a value is within an absolute distance of a target value.
     *
     * @param value The value being checked.
     * @param target The value to compare against.
     * @param range The allowed absolute distance from {@code target}.
     * @return {@code true} if {@code value} is between {@code target - range} and {@code target + range}.
     */
    public static boolean isWithinRange(double value, double target, double range) {
        return Math.abs(value - target) <= range;
    }

    /**
     * Returns whether a value is within a percentage distance of a target value.
     * <p>
     * For example, a {@code percent} of {@code 0.25} allows the value to be up to {@code 25%} above or below the target.
     * If the target is {@code 0.0}, only {@code 0.0} is considered within range.
     *
     * @param value The value being checked.
     * @param target The value to compare against.
     * @param percent The allowed distance from {@code target}, where {@code 0.25} means {@code 25%}.
     * @return {@code true} if {@code value} is within the requested percentage range.
     */
    public static boolean isWithinPercent(double value, double target, double percent) {
        if (target == 0.0) {
            return value == 0.0;
        }
        return Math.abs(value - target) <= Math.abs(target * percent);
    }

    /**
     * Abbreviates a value by dividing it, rounding it up, and appending a suffix.
     *
     * @param value The original value.
     * @param divisor The divisor used to shorten the value.
     * @param suffix The suffix to append to the shortened value.
     * @param decimals The number of decimal places to preserve before rounding up.
     * @return The abbreviated value with its suffix.
     */
    private static String abbreviateCeil(int value, int divisor, String suffix, int decimals) {
        double factor = Math.pow(10, decimals);
        double shortened = value / (double) divisor;
        double roundedUp = Math.ceil(shortened * factor) / factor;
        return ABBREVIATED.format(roundedUp) + suffix;
    }
}