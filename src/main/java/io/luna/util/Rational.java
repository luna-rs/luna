package io.luna.util;

import com.google.common.primitives.Ints;
import org.jetbrains.annotations.NotNull;

import java.math.BigInteger;
import java.util.Objects;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * A model representing a rational number (a fraction) consisting of a numerator and denominator.
 * <p>
 * This class supports normalization, comparison, and arithmetic operations between rational numbers.
 * Each {@link Rational} instance is always stored in its simplified form (lowest terms) and maintains sign
 * consistency (the denominator is always positive).
 * </p>
 * Rational numbers are immutable and thread-safe.
 *
 * @author lare96
 */
public final class Rational extends Number implements Comparable<Rational> {

    /**
     * Creates a new {@link Rational} from a double value by converting it into a fraction.
     * <p>
     * This method uses decimal precision to determine the denominator based on the number
     * of decimal places in the input.
     * </p>
     *
     * @param value The double value to convert.
     * @return A {@link Rational} representing the given double value.
     */
    public static Rational fromDouble(double value) {
        String decimalStr = String.valueOf(value);
        int decimalPlaces = decimalStr.length() - decimalStr.indexOf('.') - 1;
        long denominator = (long) Math.pow(10, decimalPlaces);
        long numerator = (long) (value * denominator);
        return new Rational(value < 0 ? -numerator : numerator, value < 0 ? -denominator : denominator);
    }

    /**
     * A rational number representing a 100% (1 in 1) chance.
     */
    public static final Rational ALWAYS = new Rational(1, 1);

    /**
     * A rational number representing a 50% (1 in 2) chance.
     */
    public static final Rational VERY_COMMON = new Rational(1, 2);

    /**
     * A rational number representing a 25% (1 in 4) chance.
     */
    public static final Rational COMMON = new Rational(1, 4);

    /**
     * A rational number representing a 10% (1 in 10) chance.
     */
    public static final Rational UNCOMMON = new Rational(1, 10);

    /**
     * A rational number representing a 2.5% (1 in 40) chance.
     */
    public static final Rational VERY_UNCOMMON = new Rational(1, 40);

    /**
     * A rational number representing a 0.7% (1 in 150) chance.
     */
    public static final Rational RARE = new Rational(1, 150);

    /**
     * A rational number representing a 0.3% (1 in 300) chance.
     */
    public static final Rational VERY_RARE = new Rational(1, 300);

    /**
     * The numerator of this rational number.
     */
    private final long numerator;

    /**
     * The denominator of this rational number.
     */
    private final long denominator;

    /**
     * Creates a new {@link Rational} with the specified numerator and denominator.
     * <p>
     * The fraction is automatically reduced to its simplest form, and the denominator is normalized to always be
     * positive. A zero denominator will result in an {@link IllegalArgumentException}.
     * </p>
     *
     * @param numerator The numerator.
     * @param denominator The denominator (must not be zero).
     * @throws IllegalArgumentException If {@code denominator == 0}.
     */
    public Rational(long numerator, long denominator) {
        checkArgument(denominator != 0, "denominator cannot be 0");

        if (denominator < 0) {
            numerator = -numerator;
            denominator = -denominator;
        }

        if (numerator != 0) {
            long gcd = gcd(Math.abs(numerator), denominator);
            numerator /= gcd;
            denominator /= gcd;
        }

        this.numerator = numerator;
        this.denominator = denominator;
    }

    /**
     * Returns a string representation of this rational number in the form {@code "numerator/denominator"}.
     * If the denominator is 1, only the numerator is returned.
     *
     * @return A string representing this rational number.
     */
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
        return Objects.hash(numerator, denominator);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (obj instanceof Rational) {
            Rational other = (Rational) obj;
            return numerator == other.numerator && denominator == other.denominator;
        }

        return false;
    }

    /**
     * Determines if this rational is greater than another.
     *
     * @param other The other rational number.
     * @return {@code true} if this value is greater, otherwise {@code false}.
     */
    public boolean greaterThan(Rational other) {
        return doubleValue() > other.doubleValue();
    }

    /**
     * Determines if this rational is less than another.
     *
     * @param other The other rational number.
     * @return {@code true} if this value is less, otherwise {@code false}.
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
     * Returns the reciprocal of this rational (swaps numerator and denominator).
     *
     * @return A new {@link Rational} representing the reciprocal.
     */
    public Rational reciprocal() {
        return new Rational(denominator, numerator);
    }

    /**
     * Adds another {@link Rational} to this one.
     *
     * @param other The other rational to add.
     * @return The resulting sum as a new {@link Rational}.
     */
    public Rational add(Rational other) {
        BigInteger num1 = BigInteger.valueOf(numerator);
        BigInteger den1 = BigInteger.valueOf(denominator);
        BigInteger num2 = BigInteger.valueOf(other.numerator);
        BigInteger den2 = BigInteger.valueOf(other.denominator);

        BigInteger newNumerator = num1.multiply(den2).add(num2.multiply(den1));
        BigInteger newDenominator = den1.multiply(den2);
        return new Rational(newNumerator.longValueExact(), newDenominator.longValueExact());
    }

    /**
     * Subtracts another {@link Rational} from this one.
     *
     * @param other The other rational to subtract.
     * @return The resulting difference as a new {@link Rational}.
     */
    public Rational subtract(Rational other) {
        BigInteger num1 = BigInteger.valueOf(numerator);
        BigInteger den1 = BigInteger.valueOf(denominator);
        BigInteger num2 = BigInteger.valueOf(other.numerator);
        BigInteger den2 = BigInteger.valueOf(other.denominator);

        BigInteger newNumerator = num1.multiply(den2).subtract(num2.multiply(den1));
        BigInteger newDenominator = den1.multiply(den2);
        return new Rational(newNumerator.longValueExact(), newDenominator.longValueExact());
    }

    /**
     * Multiplies this {@link Rational} by another.
     *
     * @param other The other rational to multiply by.
     * @return The resulting product as a new {@link Rational}.
     */
    public Rational multiply(Rational other) {
        BigInteger newNumerator = BigInteger.valueOf(numerator).multiply(BigInteger.valueOf(other.numerator));
        BigInteger newDenominator = BigInteger.valueOf(denominator).multiply(BigInteger.valueOf(other.denominator));
        return new Rational(newNumerator.longValueExact(), newDenominator.longValueExact());
    }

    /**
     * Divides this {@link Rational} by another.
     *
     * @param other The divisor.
     * @return The resulting quotient as a new {@link Rational}.
     * @throws ArithmeticException If {@code other} is zero.
     */
    public Rational divide(Rational other) {
        if (other.numerator == 0) {
            throw new ArithmeticException("Divide by zero");
        }
        BigInteger newNumerator = BigInteger.valueOf(numerator).multiply(BigInteger.valueOf(other.denominator));
        BigInteger newDenominator = BigInteger.valueOf(denominator).multiply(BigInteger.valueOf(other.numerator));


        return new Rational(newNumerator.longValueExact(), newDenominator.longValueExact());
    }

    /**
     * Computes the greatest common divisor (GCD) of two long values.
     *
     * @param numeratorOne The first value.
     * @param numeratorTwo The second value.
     * @return The GCD as an integer.
     */
    private long gcd(long numeratorOne, long numeratorTwo) {
        BigInteger numOne = BigInteger.valueOf(numeratorOne);
        BigInteger numTwo = BigInteger.valueOf(numeratorTwo);
        return numOne.gcd(numTwo).longValueExact();
    }

    /**
     * Returns the numerator of this rational number.
     *
     * @return The numerator.
     */
    public long getNumerator() {
        return numerator;
    }

    /**
     * Returns the denominator of this rational number.
     *
     * @return The denominator.
     */
    public long getDenominator() {
        return denominator;
    }

    /**
     * Compares this rational to another for ordering.
     * <p>
     * The comparison is performed using the double-precision value of each number.
     * </p>
     *
     * @param other The other rational to compare against.
     * @return A negative value if less, zero if equal, or positive if greater.
     */
    @Override
    public int compareTo(@NotNull Rational other) {
        return Long.compare(numerator * other.denominator, other.numerator * denominator);
    }
}
