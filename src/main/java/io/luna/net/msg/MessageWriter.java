package io.luna.net.msg;

import io.luna.game.model.mobile.Player;
import io.luna.net.codec.ByteMessage;

/**
 * An abstraction model representing an outbound message handler.
 *
 * @author lare96 <http://github.org/lare96>
 */
public abstract class MessageWriter {

    /**
     * Builds a buffer containing the data for this message.
     */
    public abstract ByteMessage write(Player player);

    /**
     * Converts the buffer returned by {@code write(Player)} to a game packet.
     */
    public GameMessage handleOutboundMessage(Player player) {
        ByteMessage msg = write(player);
        return new GameMessage(msg.getOpcode(), msg.getType(), msg);
    }
}
