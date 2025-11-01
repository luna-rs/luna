package io.luna.net.msg;

import io.luna.game.model.mob.Player;
import io.luna.net.codec.ByteMessage;
import io.netty.buffer.ByteBuf;

/**
 * An abstraction model that converts raw written {@link ByteMessage} buffers into {@link GameMessage} types.
 *
 * @author lare96
 */
public abstract class GameMessageWriter {

    /**
     * Writes data into a buffer.
     *
     * @param player The player.
     * @param buffer The pooled buffer.
     * @return The buffer.
     */
    public abstract ByteMessage write(Player player, ByteBuf buffer);

    /**
     * Converts the buffer returned by {@link #write(Player, ByteBuf)} into a game message.
     *
     * @param player The player.
     * @return The converted game message.
     */
    public final GameMessage toGameMessage(Player player) {
        ByteBuf pooledBuffer = ByteMessage.pooledBuffer();
        try {
            ByteMessage raw = write(player, pooledBuffer);
            return new GameMessage(raw.getOpcode(), raw.getType(), raw);
        } catch (Exception e) {
            pooledBuffer.release(pooledBuffer.refCnt());
            throw new RuntimeException(e);
        }
    }
}
