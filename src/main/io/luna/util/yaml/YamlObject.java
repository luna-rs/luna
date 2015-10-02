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
        this.value = value;
    }

    /**
     * Retrieve the underlying value as an {@code int}, type safety is not
     * guaranteed.
     * 
     * @return The value as an {@code int}.
     */
    public int asInt() {
        requireNonNull(value);
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
        requireNonNull(value);
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
        requireNonNull(value);
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
        requireNonNull(value);
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
        requireNonNull(value);
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
        requireNonNull(value);
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
        requireNonNull(value);
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
        requireNonNull(value);
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
        requireNonNull(value);
        return (String) value;
    }

    /**
     * Retrieve the underlying value as an {@code Object}.
     * 
     * @return The value as an {@code Object}, never {@code null}.
     */
    public Object asObject() {
        requireNonNull(value);
        return value;
    }

    /**
     * @return {@code true} if the underlying value is {@code null},
     *         {@code false} otherwise.
     */
    public boolean isNull() {
        return value == null;
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
