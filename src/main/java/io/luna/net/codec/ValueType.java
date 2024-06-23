package io.luna.net.codec;

/**
 * An enum representing custom Runescape value types.
 *
 * @author lare96
 */
public enum ValueType {

    /**
     * Do nothing to the value.
     */
    NORMAL,

    /**
     * Add {@code 128} to the value.
     */
    ADD,

    /**
     * Invert the sign of the value.
     */
    NEGATE,

    /**
     * Subtract {@code 128} from the value.
     */
    SUBTRACT
}