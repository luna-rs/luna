package io.luna.net.msg;

import io.luna.game.model.mobile.Player;
import io.luna.net.codec.ByteMessage;

/**
 * An outbound message builder for {@link GameMessage}s. Will build {@link ByteMessage}s which are later converted into
 * {@code GameMessage}s.
 *
 * @author lare96 <http://github.org/lare96>
 */
public abstract class OutboundGameMessage {

    /**
     * Builds a {@link ByteMessage} containing the data for this message.
     *
     * @param player The player.
     * @return The buffer containing the data.
     */
    public abstract ByteMessage writeMessage(Player player);

    /**
     * Converts the {@link ByteMessage} returned by {@code writeMessage(Player)} to a {@link GameMessage}.
     *
     * @param player The player.
     * @return The successfully converted message.
     */
    public final GameMessage toGameMessage(Player player) {
        ByteMessage msg = writeMessage(player);
        return new GameMessage(msg.getOpcode(), msg.getBuffer().readableBytes(), msg.getType(), msg);
    }
}
