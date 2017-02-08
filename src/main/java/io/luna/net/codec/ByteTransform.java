package io.luna.net.codec;

/**
 * An enum representing custom Runescape value types.
 *
 * @author lare96 <http://github.org/lare96>
 */
public enum ByteTransform {

    /**
     * Do nothing to the value.
     */
    NORMAL,

    /**
     * Add {@code 128} to the value.
     */
    A,

    /**
     * Invert the sign of the value.
     */
    C,

    /**
     * Subtract {@code 128} from the value.
     */
    S
}