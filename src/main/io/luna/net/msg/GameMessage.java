package io.luna.net.msg;

import io.luna.net.codec.ByteMessage;

/**
 * A message that can act as an inbound or outbound packet of data. It can be safely used across multiple threads due to it
 * being immutable.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class GameMessage {

    /**
     * The opcode of this message.
     */
    private final int opcode;

    /**
     * The size of this message.
     */
    private final int size;

    /**
     * The payload of this message.
     */
    private final ByteMessage payload;

    /**
     * Creates a new {@link GameMessage}.
     *
     * @param opcode The opcode of this message.
     * @param size The size of this message.
     * @param payload The payload of this message.
     */
    public GameMessage(int opcode, int size, ByteMessage payload) {
        this.opcode = opcode;
        this.size = size;
        this.payload = payload;
    }

    /**
     * @return The opcode of this message.
     */
    public int getOpcode() {
        return opcode;
    }

    /**
     * @return The size of this message.
     */
    public int getSize() {
        return size;
    }

    /**
     * @return The payload of this message.
     */
    public ByteMessage getPayload() {
        return payload;
    }
}
