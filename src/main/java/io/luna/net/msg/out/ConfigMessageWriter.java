package io.luna.net.msg.out;

import io.luna.game.model.mob.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ByteOrder;
import io.luna.net.msg.GameMessageWriter;

/**
 * A {@link GameMessageWriter} implementation that handles configuration.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class ConfigMessageWriter extends GameMessageWriter {

    /**
     * The identifier.
     */
    private final int id;

    /**
     * The value.
     */
    private final int state;

    /**
     * Creates a new {@link ConfigMessageWriter}.
     *
     * @param id The identifier.
     * @param state The value.
     */
    public ConfigMessageWriter(int id, int state) {
        this.id = id;
        this.state = state;
    }

    @Override
    public ByteMessage write(Player player) {
        return state <= Byte.MAX_VALUE ? writeByteConfig() : writeShortConfig();
    }

    /**
     * Returns a {@link ByteMessage} containing the {@code short} version of this message.
     */
    private ByteMessage writeByteConfig() {
        ByteMessage msg = ByteMessage.message(36);
        msg.putShort(id, ByteOrder.LITTLE);
        msg.put(state);
        return msg;
    }

    /**
     * Returns a {@link ByteMessage} containing the {@code byte} version of this message.
     */
    private ByteMessage writeShortConfig() {
        ByteMessage msg = ByteMessage.message(87);
        msg.putShort(id, ByteOrder.LITTLE);
        msg.putInt(state, ByteOrder.MIDDLE);
        return msg;
    }
}