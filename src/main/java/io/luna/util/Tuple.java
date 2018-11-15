package io.luna.util;

import com.google.common.base.MoreObjects;

import java.util.Objects;

/**
 * A model representing a tuple of two elements.
 *
 * @param <T> The first element type.
 * @param <U> The second element type.
 * @author lare96 <http://github.com/lare96>
 */
public final class Tuple<T, U> {

    /**
     * The first element.
     */
    private final T first;

    /**
     * The second element.
     */
    private final U second;

    /**
     * Creates a new {@link Tuple}.
     *
     * @param first The first element.
     * @param second The second element.
     */
    public Tuple(T first, U second) {
        this.first = first;
        this.second = second;
    }

    @Override
    public int hashCode() {
        return Objects.hash(first, second);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof Tuple) {
            Tuple other = (Tuple) obj;
            return Objects.equals(first, other.first) &&
                    Objects.equals(second, other.second);
        }
        return false;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).
                add("1", first).
                add("2", second).toString();
    }

    /**
     * Returns the inverse of this {@link Tuple}.
     *
     * @return This tuple, with the first and second elements swapped.
     */
    public Tuple<U, T> inverse() {
        return new Tuple<>(second, first);
    }

    /**
     * The first element.
     *
     * @return Element number one.
     */
    public T first() {
        return first;
    }

    /**
     * The second element.
     *
     * @return Element number two.
     */
    public U second() {
        return second;
    }
}