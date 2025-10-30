package io.luna.net.msg;

import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.MessageType;

import static com.google.common.base.Preconditions.checkArgument;
import static io.luna.net.msg.GameMessageReader.logger;
import static java.util.Objects.requireNonNull;
import static org.apache.logging.log4j.util.Unbox.box;

/**
 * A model representing an incoming or outgoing packet of data.
 *
 * @author lare96
 */
public final class GameMessage {

    /**
     * The opcode.
     */
    private final int opcode;

    /**
     * The size.
     */
    private final int size;

    /**
     * The message type.
     */
    private final MessageType type;

    /**
     * The payload.
     */
    private final ByteMessage payload;

    /**
     * Creates a new {@link GameMessage}.
     *
     * @param opcode The opcode.
     * @param type The message type.
     * @param payload The payload.
     */
    public GameMessage(int opcode, MessageType type, ByteMessage payload) {
        checkArgument(opcode >= 0, "opcode < 0");
        checkArgument(type != MessageType.RAW, "type == MessageType.RAW");
        requireNonNull(payload);

        this.opcode = opcode;
        this.type = type;
        this.payload = payload;
        size = payload.getBuffer().readableBytes();
    }

    /**
     * Release all references held by the underlying buffer.
     */
    public void release() {
        // Release pooled buffer.
        if (!payload.release()) {
            // Ensure that all pooled Netty buffers are deallocated here, to avoid leaks. Entering this
            // section of the code means that a buffer was not released (or retained) when it was supposed to
            // be, so we log a warning.
            logger.warn("Buffer reference count too high [opcode: {}, ref_count: {}]",
                    box(opcode), box(payload.refCnt()));
            payload.releaseAll();
        }
    }

    /**
     * @return The opcode.
     */
    public int getOpcode() {
        return opcode;
    }

    /**
     * @return The size.
     */
    public int getSize() {
        return size;
    }

    /**
     * @return The message type.
     */
    public MessageType getType() {
        return type;
    }

    /**
     * @return The payload.
     */
    public ByteMessage getPayload() {
        return payload;
    }
}
