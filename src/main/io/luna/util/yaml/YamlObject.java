package io.luna.util.yaml;

import static java.util.Objects.requireNonNull;

import com.google.common.primitives.Primitives;

/**
 * Represents an immutable value within a {@code YAML} key-value pair.
 * 
 * @author lare96 <http://github.org/lare96>
 */
public final class YamlObject {

    /**
     * The immutable value within this {@code YamlObject}.
     */
    private final Object value;

    /**
     * Creates a new {@link YamlObject}.
     *
     * @param value The immutable value within this {@code YamlObject}.
     */
    YamlObject(Object value) {
        this.value = requireNonNull(value);
    }

    /**
     * Retrieve the underlying value as an {@code int}, type safety is not
     * guaranteed.
     * 
     * @return The value as an {@code int}.
     */
    public int asInt() {
        if (value instanceof String) {
            return Integer.parseInt(asString());
        }
        return (int) value;
    }

    /**
     * Retrieve the underlying value as an {@code long}, type safety is not
     * guaranteed.
     * 
     * @return The value as an {@code long}.
     */
    public long asLong() {
        if (value instanceof String) {
            return Long.parseLong(asString());
        }
        return (long) value;
    }

    /**
     * Retrieve the underlying value as an {@code double}, type safety is not
     * guaranteed.
     * 
     * @return The value as an {@code double}.
     */
    public double asDouble() {
        if (value instanceof String) {
            return Double.parseDouble(asString());
        }
        return (double) value;
    }

    /**
     * Retrieve the underlying value as an {@code float}, type safety is not
     * guaranteed.
     * 
     * @return The value as an {@code float}.
     */
    public float asFloat() {
        if (value instanceof String) {
            return Float.parseFloat(asString());
        }
        return (float) value;
    }

    /**
     * Retrieve the underlying value as an {@code short}, type safety is not
     * guaranteed.
     * 
     * @return The value as an {@code short}.
     */
    public short asShort() {
        if (value instanceof String) {
            return Short.parseShort(asString());
        }
        return (short) value;
    }

    /**
     * Retrieve the underlying value as an {@code byte}, type safety is not
     * guaranteed.
     * 
     * @return The value as an {@code byte}.
     */
    public byte asByte() {
        if (value instanceof String) {
            return Byte.parseByte(asString());
        }
        return (byte) value;
    }

    /**
     * Retrieve the underlying value as an {@code char}, type safety is not
     * guaranteed.
     * 
     * @return The value as an {@code char}.
     */
    public char asChar() {
        if (value instanceof String) {
            return asString().charAt(0);
        }
        return (char) value;
    }

    /**
     * Retrieve the underlying value as an {@code boolean}, type safety is not
     * guaranteed.
     * 
     * @return The value as an {@code boolean}.
     */
    public boolean asBoolean() {
        if (value instanceof String) {
            return Boolean.parseBoolean(asString());
        }
        return (boolean) value;
    }

    /**
     * Retrieve the underlying value as an {@code String}, type safety is not
     * guaranteed.
     * 
     * @return The value as an {@code String}.
     */
    public String asString() {
        return (String) value;
    }

    /**
     * Retrieve the underlying value as an {@code Object}.
     * 
     * @return The value as an {@code Object}.
     */
    public Object asObject() {
        return value;
    }

    /**
     * Retrieve the underlying value as {@code T}, type safety is not
     * guaranteed.
     * 
     * @return The value as {@code T}.
     */
    public <T> T asType(Class<T> type) {
        return Primitives.wrap(type).cast(value);
    }
}
