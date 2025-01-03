package io.luna.util;

import com.google.common.primitives.Ints;
import org.jetbrains.annotations.NotNull;

import java.math.BigInteger;
import java.util.Objects;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * A model representing a rational number.
 *
 * @author lare96
 */
public final class Rational extends Number implements Comparable<Rational> {

    /**
     * Creates a new {@link Rational} instance from a double value.
     *
     * @param value The double value.
     * @return The newly created rational value.
     */
    public static Rational fromDouble(double value) {
        String decimalStr = String.valueOf(value);
        int decimalPlaces = decimalStr.length() - decimalStr.indexOf('.') - 1;
        long denominator = (long) Math.pow(10, decimalPlaces);
        long numerator = (long) (value * denominator);
        return new Rational(value < 0 ? -numerator : numerator, value < 0 ? -denominator : denominator);
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
     * A rational number describing a {@code COMMON} chance (25%, 1 in 4 chance).
     */
    public static final Rational COMMON = new Rational(1, 4);

    /**
     * A rational number describing an {@code UNCOMMON} chance (10%, 1 in 10 chance).
     */
    public static final Rational UNCOMMON = new Rational(1, 10);

    /**
     * A rational number describing a {@code VERY_UNCOMMON} chance (2.5%, 1 in 40 chance).
     */
    public static final Rational VERY_UNCOMMON = new Rational(1, 40);

    /**
     * A rational number describing a {@code RARE} chance (0.7%, 1 in 150 chance).
     */
    public static final Rational RARE = new Rational(1, 150);

    /**
     * A rational number describing an {@code VERY_RARE} chance (0.3%, 1 in 300 chance).
     */
    public static final Rational VERY_RARE = new Rational(1, 300);

    /**
     * The numerator.
     */
    private final long numerator;

    /**
     * The denominator.
     */
    private final long denominator;

    /**
     * Create a {@link Rational}.
     *
     * @param numerator The numerator.
     * @param denominator The denominator.
     */
    public Rational(long numerator, long denominator) {
        checkArgument(denominator != 0, "denominator cannot be 0");

        if (denominator < 0) {
            numerator = numerator * -1;
            denominator = denominator * -1;
        }

        if (numerator != 0) {
            int gcd = gcd(Math.abs(numerator), denominator);

            numerator = numerator / gcd;
            denominator = denominator / gcd;
        }

        this.numerator = numerator;
        this.denominator = denominator;
    }

    @Override
    public String toString() {
        if (numerator == 0) {
            return "0";
        }

        if (denominator == 1) {
            return Long.toString(numerator);
        }

        return numerator + "/" + denominator;
    }

    @Override
    public int hashCode() {
        return Objects.hash(doubleValue());
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (obj instanceof Rational) {
            Rational other = (Rational) obj;
            return doubleValue() == other.doubleValue();
        }

        return false;
    }

    /**
     * Determines if this rational is greater than another.
     *
     * @param other The other rational.
     * @return {@code true} If this number is greater.
     */
    public boolean greaterThan(Rational other) {
        return doubleValue() > other.doubleValue();
    }

    /**
     * Determines if this rational is less than another.
     *
     * @param other The other rational.
     * @return {@code true} If this number is lesser.
     */
    public boolean lessThan(Rational other) {
        return doubleValue() < other.doubleValue();
    }

    @Override
    public int intValue() {
        return Ints.saturatedCast(longValue());
    }

    @Override
    public long longValue() {
        return numerator / denominator;
    }

    @Override
    public float floatValue() {
        return ((float) numerator / (float) denominator);
    }

    @Override
    public double doubleValue() {
        return ((double) numerator / (double) denominator);
    }

    /**
     * Returns the reciprocal of this rational (flips the numerator and denominator).
     *
     * @return The reciprocal.
     */
    public Rational reciprocal() {
        return new Rational(denominator, numerator);
    }

    /**
     * Adds {@code other} with this rational. Returns a new Rational instance.
     *
     * @param other The rational number to add.
     * @return The new rational.
     */
    public Rational add(Rational other) {
        long commonDenominator = denominator * other.denominator;
        long numeratorOne = numerator * other.denominator;
        long numeratorTwo = other.numerator * denominator;
        long numeratorSum = numeratorOne + numeratorTwo;

        return new Rational(numeratorSum, commonDenominator);
    }

    /**
     * Subtracts {@code other} from this rational. Returns a new Rational instance.
     *
     * @param other The rational number to subtract.
     * @return The new rational.
     */
    public Rational subtract(Rational other) {
        long commonDenominator = denominator * other.denominator;
        long numeratorOne = numerator * other.denominator;
        long numeratorTwo = other.numerator * denominator;
        long numeratorDifference = numeratorOne - numeratorTwo;

        return new Rational(numeratorDifference, commonDenominator);
    }

    /**
     * Multiplies this rational with {@code other}. Returns a new Rational instance.
     *
     * @param other The rational number to multiply.
     * @return The new rational.
     */
    public Rational multiply(Rational other) {
        long n = numerator * other.numerator;
        long d = denominator * other.denominator;

        return new Rational(n, d);
    }

    /**
     * Divides this rational with {@code other}. Returns a new Rational instance.
     *
     * @param other The rational number to divide.
     * @return The new rational.
     */
    public Rational divide(Rational other) {
        return multiply(other.reciprocal());
    }

    /**
     * Determines the greatest common denominator.
     *
     * @param numeratorOne The first numerator.
     * @param numeratorTwo The second numerator.
     */
    private int gcd(long numeratorOne, long numeratorTwo) {
        BigInteger numOne = BigInteger.valueOf(numeratorOne);
        BigInteger numTwo = BigInteger.valueOf(numeratorTwo);
        return numOne.gcd(numTwo).intValue();
    }

    /**
     * @return The numerator.
     */
    public long getNumerator() {
        return numerator;
    }

    /**
     * @return The denominator.
     */
    public long getDenominator() {
        return denominator;
    }

    @Override
    public int compareTo(@NotNull Rational o) {
        return Double.compare(doubleValue(), o.doubleValue());
    }
}
