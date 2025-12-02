package io.luna.net.msg;

import io.luna.game.model.mob.Player;
import io.luna.net.codec.ByteMessage;
import io.netty.buffer.ByteBuf;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * An abstraction model that converts raw written {@link ByteMessage} buffers into {@link GameMessage} types.
 *
 * @author lare96
 */
public abstract class GameMessageWriter {

    private final Logger logger = LogManager.getLogger();

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
            if (pooledBuffer.refCnt() > 0) {
                pooledBuffer.release(pooledBuffer.refCnt());
            }
            logger.catching(e);
            return null;
        }
    }
}
