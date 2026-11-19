package io.luna.net.msg;

import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.MessageType;

import java.util.function.BooleanSupplier;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

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
     * A game-thread validity check for a queued synthetic equipment click.
     */
    private final BooleanSupplier dispatchGuard;

    /**
     * Creates a new {@link GameMessage}.
     *
     * @param opcode The opcode.
     * @param type The message type.
     * @param payload The payload.
     */
    public GameMessage(int opcode, MessageType type, ByteMessage payload) {
        this(opcode, type, payload, null);
    }

    /**
     * Creates an in-memory message whose dispatch is conditional on its originating activity.
     */
    public GameMessage(int opcode, MessageType type, ByteMessage payload, BooleanSupplier dispatchGuard) {
        checkArgument(opcode >= 0, "opcode < 0");
        checkArgument(type != MessageType.RAW, "type == MessageType.RAW");
        requireNonNull(payload);

        this.opcode = opcode;
        this.type = type;
        this.payload = payload;
        this.dispatchGuard = dispatchGuard;
        size = payload.getBuffer().readableBytes();
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

    public boolean hasDispatchGuard() {
        return dispatchGuard != null;
    }

    public boolean canDispatch() {
        return dispatchGuard == null || dispatchGuard.getAsBoolean();
    }
}
