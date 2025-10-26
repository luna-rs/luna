package io.luna.util;

import com.google.common.collect.BoundType;
import com.google.common.collect.Range;
import com.google.common.math.IntMath;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import static com.google.common.base.Preconditions.checkArgument;


/**
 * A static utility class that provides additional functionality for generating pseudo-random numbers. All functions
 * in this class are backed by {@link ThreadLocalRandom} rather than {@link java.util.Random}.
 *
 * @author lare96
 * @see <a href="http://java-performance.info/java-util-random-java-util-concurrent-threadlocalrandom-multithreaded-environments/">
 * Java performance article comparing Random and ThreadLocalRandom</a>
 */
public final class RandomUtils {

    /**
     * Rolls a pseudo-random chance based on a {@link Rational} probability.
     *
     * @param rational The chance, where numerator/denominator represents the probability.
     * @return {@code true} if the roll succeeds, {@code false} otherwise.
     */
    public static boolean roll(Rational rational) {
        if (rational.getNumerator() <= 0) {
            return false;
        } else if (rational.getNumerator() >= rational.getDenominator()) {
            return true;
        } else return ThreadLocalRandom.current().nextLong(0, rational.getDenominator()) < rational.getNumerator();
    }

    /**
     * Performs a weighted random selection from a map of elements to their respective weights.
     *
     * @param weights A map containing elements and their associated weight values.
     * @param <T> The element type.
     * @return A randomly chosen element based on its weight, or {@code null} if all weights are zero.
     */
    public static <T> T weightedRoll(Map<T, Double> weights) {
        double total = 0.0;
        for (double next : weights.values()) {
            total += next;
        }
        if (total == 0.0) {
            return null;
        }
        double roll = nextDouble() * total;
        double current = 0.0;
        for (Map.Entry<T, Double> entry : weights.entrySet()) {
            current += entry.getValue();
            if (roll < current) {
                return entry.getKey();
            }
        }
        return weights.keySet().iterator().next();
    }

    /**
     * Generates a random double between {@code 0.0} (inclusive) and {@code 1.0} (exclusive).
     *
     * @return The generated random double.
     */
    public static double nextDouble() {
        return ThreadLocalRandom.current().nextDouble();
    }

    /**
     * Rolls a pseudo-random percent chance using an integer between {@code 0} and {@code 100}.
     *
     * @param value The percent chance to succeed (0–100).
     * @return {@code true} if successful, {@code false} otherwise.
     */
    public static boolean rollPercent(int value) {
        value = Math.max(0, Math.min(value, 100));
        return ThreadLocalRandom.current().nextInt(100) < value;
    }

    /**
     * Rolls a pseudo-random chance using a double between {@code 0.0} and {@code 1.0}.
     *
     * @param value The chance to succeed (0.0–1.0).
     * @return {@code true} if successful, {@code false} otherwise.
     */
    public static boolean rollPercent(double value) {
        value = Math.max(0.0, Math.min(value, 1.0));
        return ThreadLocalRandom.current().nextDouble() < value;
    }

    /**
     * Returns a random integer between two inclusive bounds.
     *
     * @param min The minimum bound (inclusive).
     * @param max The maximum bound (inclusive).
     * @return The generated random integer.
     * @throws IllegalArgumentException If {@code max < min}.
     */
    public static int inclusive(int min, int max) {
        checkArgument(max >= min, "max < min");
        return ThreadLocalRandom.current().nextInt(min, IntMath.saturatedAdd(max, 1));
    }

    /**
     * Returns a random integer between {@code 0} (inclusive) and {@code range} (inclusive).
     *
     * @param range The upper bound (inclusive).
     * @return The generated random integer.
     */
    public static int inclusive(int range) {
        return inclusive(0, range);
    }

    /**
     * Returns a random integer between two bounds, where the upper bound is exclusive.
     *
     * @param min The lower bound (inclusive).
     * @param max The upper bound (exclusive).
     * @return The generated random integer.
     */
    public static int exclusive(int min, int max) {
        return ThreadLocalRandom.current().nextInt(min, max);
    }

    /**
     * Returns a random integer between {@code 0} (inclusive) and {@code range} (exclusive).
     *
     * @param range The upper bound (exclusive).
     * @return The generated random integer.
     */
    public static int exclusive(int range) {
        return exclusive(0, range);
    }

    /**
     * Returns a random boolean value.
     *
     * @return {@code true} or {@code false}, chosen at random.
     */
    public static boolean randomBoolean() {
        return ThreadLocalRandom.current().nextBoolean();
    }

    /**
     * Returns a random element from the specified array.
     *
     * @param array The array to select from.
     * @param <T> The element type.
     * @return A random element from the array.
     */
    @SafeVarargs
    public static <T> T randomFrom(T... array) {
        return array.length == 1 ? array[0] : array[ThreadLocalRandom.current().nextInt(array.length)];
    }

    /**
     * Returns a random element from a given array.
     *
     * @param array The array to select from.
     * @param <T> The element type.
     * @return A random element, or {@code null} if the array is empty.
     */
    public static <T> T random(T[] array) {
        if (array.length == 0) return null;
        return array[ThreadLocalRandom.current().nextInt(array.length)];
    }

    /* Random element overloads for all primitive types. */
    public static int random(int[] array) {
        return array[ThreadLocalRandom.current().nextInt(array.length)];
    }

    public static long random(long[] array) {
        return array[ThreadLocalRandom.current().nextInt(array.length)];
    }

    public static double random(double[] array) {
        return array[ThreadLocalRandom.current().nextInt(array.length)];
    }

    public static short random(short[] array) {
        return array[ThreadLocalRandom.current().nextInt(array.length)];
    }

    public static byte random(byte[] array) {
        return array[ThreadLocalRandom.current().nextInt(array.length)];
    }

    public static float random(float[] array) {
        return array[ThreadLocalRandom.current().nextInt(array.length)];
    }

    public static boolean random(boolean[] array) {
        return array[ThreadLocalRandom.current().nextInt(array.length)];
    }

    public static char random(char[] array) {
        return array[ThreadLocalRandom.current().nextInt(array.length)];
    }

    /**
     * Returns a random element from a {@link List}.
     *
     * @param list The list to select from.
     * @param <T> The element type.
     * @return A random element, or {@code null} if the list is empty.
     */
    public static <T> T random(List<T> list) {
        if (list.isEmpty()) return null;
        return list.get(ThreadLocalRandom.current().nextInt(list.size()));
    }

    /**
     * Returns a random integer within a specified {@link Range}.
     *
     * @param range The range to select from.
     * @return A random integer within the range.
     */
    public static int random(Range<Integer> range) {
        int low = range.hasLowerBound() ? range.lowerEndpoint() : Integer.MIN_VALUE;
        int high = range.hasUpperBound() ? range.upperEndpoint() : Integer.MAX_VALUE;
        if (range.upperBoundType() == BoundType.OPEN && range.lowerBoundType() == BoundType.CLOSED)
            return inclusive(low, high - 1);
        if (range.upperBoundType() == BoundType.CLOSED && range.lowerBoundType() == BoundType.OPEN)
            return inclusive(low + 1, high);
        if (range.upperBoundType() == BoundType.OPEN && range.lowerBoundType() == BoundType.OPEN)
            return inclusive(low + 1, high - 1);
        return inclusive(low, high);
    }

    /* Shuffle overloads for all supported types. */
    public static <T> T[] shuffle(T[] array) {
        for (int i = array.length - 1; i > 0; i--) {
            int index = ThreadLocalRandom.current().nextInt(i + 1);
            T a = array[index];
            array[index] = array[i];
            array[i] = a;
        }
        return array;
    }

    public static int[] shuffle(int[] array) {
        return shuffleArray(array);
    }

    public static long[] shuffle(long[] array) {
        return shuffleArray(array);
    }

    public static double[] shuffle(double[] array) {
        return shuffleArray(array);
    }

    public static short[] shuffle(short[] array) {
        return shuffleArray(array);
    }

    public static byte[] shuffle(byte[] array) {
        return shuffleArray(array);
    }

    public static float[] shuffle(float[] array) {
        return shuffleArray(array);
    }

    public static boolean[] shuffle(boolean[] array) {
        return shuffleArray(array);
    }

    public static char[] shuffle(char[] array) {
        return shuffleArray(array);
    }

    /**
     * A generic helper method used by all primitive shuffle methods.
     */
    private static <A> A shuffleArray(A array) {
        int length = java.lang.reflect.Array.getLength(array);
        for (int i = length - 1; i > 0; i--) {
            int index = ThreadLocalRandom.current().nextInt(i + 1);
            Object a = java.lang.reflect.Array.get(array, index);
            java.lang.reflect.Array.set(array, index, java.lang.reflect.Array.get(array, i));
            java.lang.reflect.Array.set(array, i, a);
        }
        return array;
    }

    /**
     * Private constructor to prevent external instantiation.
     */
    private RandomUtils() {
    }
}
