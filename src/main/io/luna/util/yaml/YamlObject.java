package io.luna.util.yaml;

import com.google.common.primitives.Primitives;

public final class YamlObject {

    private final Object value;

    YamlObject(Object value) {
        this.value = value;
    }

    public int asInt() {
        return (int) value;
    }

    public long asLong() {
        return (long) value;
    }

    public double asDouble() {
        return (double) value;
    }

    public float asFloat() {
        return (float) value;
    }

    public short asShort() {
        return (short) value;
    }

    public byte asByte() {
        return (byte) value;
    }

    public char asChar() {
        return (char) value;
    }

    public boolean asBoolean() {
        return (boolean) value;
    }

    public String asString() {
        return (String) value;
    }

    public Object asObject() {
        return value;
    }

    public <T> T asType(Class<T> type) {
        return Primitives.wrap(type).cast(value);
    }
}
