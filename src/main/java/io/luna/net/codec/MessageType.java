package io.luna.net.codec;

/**
 * An enum representing different types of outgoing data.
 *
 * @author lare96
 */
public enum MessageType {

    /**
     * A non-game packet of data.
     */
    RAW,

    /**
     * A fixed length game packet.
     */
    FIXED,

    /**
     * A variable byte length game packet.
     */
    VAR,

    /**
     * A variable short length game packet.
     */
    VAR_SHORT
}
