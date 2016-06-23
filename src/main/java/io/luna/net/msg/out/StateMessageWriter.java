package io.luna.net.msg.out;

import io.luna.game.model.mobile.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ByteOrder;
import io.luna.net.msg.MessageWriter;

/**
 * An {@link MessageWriter} implementation that handles both {@code byte} and {@code short} state messages.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class StateMessageWriter extends MessageWriter {

    /**
     * The identifier for the state.
     */
    private final int id;

    /**
     * The value of the state.
     */
    private final int state;

    /**
     * Creates a new {@link StateMessageWriter}.
     *
     * @param id The identifier for the state.
     * @param state The value of the state.
     */
    public StateMessageWriter(int id, int state) {
        this.id = id;
        this.state = state;
    }

    @Override
    public ByteMessage write(Player player) {
        return state <= Byte.MAX_VALUE ? writeByteState() : writeShortState();
    }

    /**
     * Returns a {@link ByteMessage} containing the {@code short} version of this message.
     */
    private ByteMessage writeByteState() {
        ByteMessage msg = ByteMessage.message(36);
        msg.putShort(id, ByteOrder.LITTLE);
        msg.put(state);
        return msg;
    }

    /**
     * Returns a {@link ByteMessage} containing the {@code byte} version of this message.
     */
    private ByteMessage writeShortState() {
        ByteMessage msg = ByteMessage.message(87);
        msg.putShort(id, ByteOrder.LITTLE);
        msg.putInt(state, ByteOrder.MIDDLE);
        return msg;
    }
}