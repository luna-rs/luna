package io.luna.net.msg;

import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.MessageType;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

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
     * The type of this message.
     */
    private final MessageType type;

    /**
     * The payload of this message.
     */
    private final ByteMessage payload;

    /**
     * Creates a new {@link GameMessage}.
     *
     * @param opcode The opcode of this message.
     * @param size The size of this message.
     * @param type The type of this message.
     * @param payload The payload of this message.
     */
    public GameMessage(int opcode, int size, MessageType type, ByteMessage payload) {
        checkArgument(opcode >= 0, "opcode < 0");
        checkArgument(size >= 0, "size < 0");
        checkArgument(type != MessageType.RAW, "type == MessageType.RAW");

        this.opcode = opcode;
        this.size = size;
        this.type = type;
        this.payload = requireNonNull(payload);
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
     * @return The type of this message.
     */
    public MessageType getType() {
        return type;
    }

    /**
     * @return The payload of this message.
     */
    public ByteMessage getPayload() {
        return payload;
    }
}
