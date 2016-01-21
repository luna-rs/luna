package io.luna.net.msg.out;

import io.luna.game.model.mobile.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.MessageType;
import io.luna.net.msg.OutboundGameMessage;

/**
 * An {@link OutboundGameMessage} that sends the {@link Player} a game message located in the chatbox.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class SendInfoMessage extends OutboundGameMessage {

    /**
     * The message to write to the chatbox.
     */
    private final String message;

    /**
     * Creates a new {@link SendInfoMessage}.
     *
     * @param message The message to write to the chatbox.
     */
    public SendInfoMessage(String message) {
        this.message = message;
    }

    @Override
    public ByteMessage writeMessage(Player player) {
        ByteMessage msg = ByteMessage.message(253, MessageType.VARIABLE);
        msg.putString(message);
        return msg;
    }
}
