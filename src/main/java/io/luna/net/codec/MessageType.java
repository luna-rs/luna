package io.luna.net.codec;

/**
 * An enumerated type whose elements represent the possible {@link ByteMessage} types.
 *
 * @author lare96 <http://github.org/lare96>
 */
public enum MessageType {

    /**
     * Represents a non-game packet of data.
     */
    RAW,

    /**
     * Represents a fixed length game packet.
     */
    FIXED,

    /**
     * Represents a variable byte length game packet.
     */
    VARIABLE,

    /**
     * Represents a variable short length game packet.
     */
    VARIABLE_SHORT
}
