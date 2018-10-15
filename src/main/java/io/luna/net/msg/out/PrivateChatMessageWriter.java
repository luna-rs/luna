package io.luna.net.msg.out;

import io.luna.game.model.mob.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.MessageType;
import io.luna.net.msg.GameMessageWriter;

/**
 * A {@link GameMessageWriter} implementation that sends a private message.
 *
 * @author lare96 <http://github.com/lare96>
 */
public final class PrivateChatMessageWriter extends GameMessageWriter {

    /**
     * The receiver of the message.
     */
    private final long name;

    /**
     * The message to send.
     */
    private final byte[] message;

    /**
     * Creates a new {@link PrivateChatMessageWriter}.
     *
     * @param name The receiver of the message.
     * @param message The message to send.
     */
    public PrivateChatMessageWriter(long name, byte[] message) {
        this.name = name;
        this.message = message;
    }

    @Override
    public ByteMessage write(Player player) {
        ByteMessage msg = ByteMessage.message(196, MessageType.VAR);
        msg.putLong(name);
        msg.putInt(player.newPrivateMessageId());
        msg.put(player.getRights().getClientValue());
        msg.putBytes(message);
        return msg;
    }
}