package io.luna.game.model.mobile.attr;

import com.google.common.base.MoreObjects;

import java.util.Objects;

/**
 * A wrapper that contains simple functions to retrieve and modify the value mapped with an {@link AttributeKey}.
 *
 * @param <T> The {@link Object} type represented by this value.
 * @author lare96 <http://github.org/lare96>
 */
public final class AttributeValue<T> {

    /**
     * The value within this wrapper.
     */
    private T value;

    /**
     * Creates a new {@link AttributeValue}.
     *
     * @param value The value within this wrapper.
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
    public String toString() {
        return MoreObjects.toStringHelper(this).add("value", value).toString();
    }

    /**
     * @return The value within this wrapper.
     */
    public T get() {
        return value;
    }

    /**
     * Sets the value for {@link #value}.
     */
    public void set(T value) {
        this.value = value;
    }
}
