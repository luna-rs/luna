package io.luna.util;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Consumer;
import java.util.function.IntPredicate;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

/**
 * A static-utility class that contains functions for the Optional API. These functions are meant to be
 * statically imported when used.
 *
 * @author lare96 <http://github.com/lare96>
 */
public final class OptionalUtils {

    /**
     * Maps an {@link Optional} to an {@link OptionalInt}.
     *
     * @param optional The optional to map.
     * @param mapper The mapper function.
     * @param <T> The optional type.
     * @return The {@link OptionalInt}.
     */
    public static <T> OptionalInt mapToInt(Optional<T> optional, ToIntFunction<T> mapper) {
        return optional.map(value -> OptionalInt.of(mapper.applyAsInt(value)))
                       .orElseGet(OptionalInt::empty);
    }

    /**
     * Filters an {@link OptionalInt}.
     *
     * @param optional The optional to filter.
     * @param predicate The predicate to filter with.
     * @return The optional if predicate passes, otherwise an empty optional.
     */
    public static OptionalInt filter(OptionalInt optional, IntPredicate predicate) {
        if (optional.isPresent()) {
            int value = optional.getAsInt();
            if (predicate.test(value)) {
                return optional;
            }
        }
        return OptionalInt.empty();
    }

    /**
     * Determines if the optional's value is equal to {@code value}.
     *
     * @param optional The optional.
     * @param value The value to compare.
     * @param <T> The value type.
     * @return {@code true} if the two values are equal.
     */
    public static <T> boolean matches(Optional<T> optional, Object value) {
        return optional.filter(val -> Objects.equals(val, value)).isPresent();
    }

    /**
     * Determines if the optional's value is equal to the supplied {@code value}.
     *
     * @param optional The optional.
     * @param value The value supplier.
     * @param <T> The value type.
     * @return {@code true} if the two values are equal.
     */
    public static <T> boolean matches(Optional<T> optional, Supplier<T> value) {
        if (!optional.isPresent()) {
            return false;
        }
        Object optionalValue = optional.get();
        Object supplierValue = value.get();
        return Objects.equals(optionalValue, supplierValue);
    }

    /**
     * Determines if the optional's value is equal to the supplied {@code value}.
     *
     * @param optional The optional.
     * @param value The value supplier.
     * @return {@code true} if the two values are equal.
     */
    public static boolean matches(OptionalInt optional, IntSupplier value) {
        return optional.isPresent() && optional.getAsInt() == value.getAsInt();
    }

    /**
     * Determines if the optional's value is equal to {@code value}.
     *
     * @param optional The optional.
     * @param value The value to compare.
     * @return {@code true} if the two values are equal.
     */
    public static boolean matches(OptionalInt optional, int value) {
        return optional.isPresent() && optional.getAsInt() == value;
    }

    /**
     * Determines if the optional's value passes {@code predicate}.
     *
     * @param optional The optional.
     * @param predicate The predicate to test against.
     * @return {@code true} if the predicate passes.
     */
    public static boolean matches(OptionalInt optional, IntPredicate predicate) {
        return filter(optional, predicate).isPresent();
    }

    /**
     * Executes {@code consumer} on the optional's value if the value is present.
     *
     * @param optional The optional.
     * @param consumer The consumer.
     * @param <T> The value type.
     * @return {@code true} if the consumer was applied.
     */
    public static <T> boolean ifPresent(Optional<T> optional, Consumer<T> consumer) {
        if (optional.isPresent()) {
            consumer.accept(optional.get());
            return true;
        }
        return false;
    }

    /**
     * A private constructor to discourage external instantiation.
     */
    private OptionalUtils() {
    }
}