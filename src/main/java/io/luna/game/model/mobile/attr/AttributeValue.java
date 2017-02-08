package io.luna.game.model.mobile.attr;

import java.util.Objects;

/**
 * A model representing a value within an attribute.
 *
 * @param <T> The Object type represented by this value.
 * @author lare96 <http://github.org/lare96>
 */
public final class AttributeValue<T> {

    /**
     * The value.
     */
    private T value;

    /**
     * Creates a new {@link AttributeValue}.
     *
     * @param value The value.
     */
    public AttributeValue(T value) {
        this.value = value;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof AttributeValue<?>) {
            AttributeValue<?> other = (AttributeValue<?>) obj;
            return Objects.equals(value, other.value);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    /**
     * @return The value.
     */
    public T get() {
        return value;
    }

    /**
     * Sets the value.
     */
    public void set(T value) {
        this.value = value;
    }
}
