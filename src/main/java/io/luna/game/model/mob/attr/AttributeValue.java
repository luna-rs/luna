package io.luna.game.model.mob.attr;

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
        if (!(obj instanceof AttributeValue<?>)) {
            return false;
        }
        
        return Objects.equals(value, ((AttributeValue<?>) obj).value);
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
     *
     * @param value The new value.
     */
    public void set(T value) {
        this.value = value;
    }
}
