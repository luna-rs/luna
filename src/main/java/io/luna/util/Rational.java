package io.luna.util;

import com.google.common.base.MoreObjects;

import java.util.Objects;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * A rational number represented by one numerator and denominator.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class Rational extends Number {

    private final int numerator;
    private final int denominator;

    public Rational(int numerator, int denominator) {
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
        String result;

        if (numerator == 0)
            result = "0";
        else if (denominator == 1)
            result = numerator + "";
        else
            result = numerator + "/" + denominator;

        return result;

    }

    public String toObjString() {
        return MoreObjects.toStringHelper(this).
            add("numerator", numerator).
            add("denominator", denominator).toString();
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

    public boolean greaterThan(Rational rational) {
        return doubleValue() > rational.doubleValue();
    }

    public boolean lessThan(Rational rational) {
        return doubleValue() < rational.doubleValue();
    }

    @Override
    public int intValue() {
        return numerator / denominator;
    }

    @Override
    public long longValue() {
        return (long) intValue();
    }

    @Override
    public float floatValue() {
        return ((float) numerator / (float) denominator);
    }

    @Override
    public double doubleValue() {
        return ((double) numerator / (double) denominator);
    }

    public Rational reciprocal() {
        return new Rational(denominator, numerator);
    }

    public Rational add(Rational rational) {
        int commonDenominator = denominator * rational.denominator;
        int numeratorOne = numerator * rational.denominator;
        int numeratorTwo = rational.numerator * denominator;
        int numeratorSum = numeratorOne + numeratorTwo;

        return new Rational(numeratorSum, commonDenominator);
    }

    public Rational subtract(Rational rational) {
        int commonDenominator = denominator * rational.denominator;
        int numeratorOne = numerator * rational.denominator;
        int numeratorTwo = rational.numerator * denominator;
        int numeratorDifference = numeratorOne - numeratorTwo;

        return new Rational(numeratorDifference, commonDenominator);
    }

    public Rational multiply(Rational rational) {
        int n = numerator * rational.numerator;
        int d = denominator * rational.denominator;

        return new Rational(n, d);
    }

    public Rational divide(Rational rational) {
        return multiply(rational.reciprocal());
    }

    private int gcd(int numeratorOne, int numeratorTwo) {
        while (numeratorOne != numeratorTwo) {
            if (numeratorOne > numeratorTwo) {
                numeratorOne = numeratorOne - numeratorTwo;
            } else {
                numeratorTwo = numeratorTwo - numeratorOne;
            }
        }
        return numeratorOne;
    }

    public int getNumerator() {
        return numerator;
    }

    public int getDenominator() {
        return denominator;
    }
}
