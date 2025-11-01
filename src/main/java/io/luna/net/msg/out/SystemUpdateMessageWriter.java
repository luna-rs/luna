package io.luna.net.msg.out;

import io.luna.game.model.mob.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ByteOrder;
import io.luna.net.msg.GameMessageWriter;
import io.netty.buffer.ByteBuf;

/**
 * A {@link GameMessageWriter} implementation that displays a system update message. This packet does not
 * log the player out or terminate the server.
 *
 * @author lare96 
 */
public final class SystemUpdateMessageWriter extends GameMessageWriter {

    /**
     * The amount of ticks to show the message for.
     */
    private final int ticks;

    /**
     * Creates a new {@link SystemUpdateMessageWriter}.
     *
     * @param ticks The amount of ticks to show the message for.
     */
    public SystemUpdateMessageWriter(int ticks) {
        this.ticks = ticks;
    }

    @Override
    public ByteMessage write(Player player, ByteBuf buffer) {
        ByteMessage msg = ByteMessage.message(190, buffer);
        msg.putShort(ticks, ByteOrder.LITTLE);
        return msg;
    }
}