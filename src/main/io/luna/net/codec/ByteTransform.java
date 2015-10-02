package io.luna.net.codec;

/**
 * The enumerated type whose elements represent the possible custom RuneScape
 * value types.
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