package io.luna.net.msg.out;

import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.PlayerRights;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.MessageType;
import io.luna.net.msg.GameMessageWriter;
import io.netty.buffer.ByteBuf;

/**
 * A {@link GameMessageWriter} implementation that sends a private message.
 *
 * @author lare96 
 */
public final class PrivateChatMessageWriter extends GameMessageWriter {

    /**
     * The sender of the message.
     */
    private final long name;

    /**
     * The message to send.
     */
    private final byte[] message;

    /**
     * The sender's client rank, captured before the packet is written for its recipient.
     */
    private final int rights;

    /**
     * Creates a new {@link PrivateChatMessageWriter}.
     *
     * @param name The sender of the message.
     * @param message The message to send.
     */
    public PrivateChatMessageWriter(long name, byte[] message) {
        this(name, message, PlayerRights.PLAYER);
    }

    /**
     * Creates a private message carrying the sender's rank independently of its recipient.
     *
     * @param name The sender of the message.
     * @param message The packed message text.
     * @param senderRights The sender's authority level.
     */
    public PrivateChatMessageWriter(long name, byte[] message, PlayerRights senderRights) {
        this.name = name;
        this.message = message;
        rights = senderRights.getClientValue();
    }

    @Override
    public ByteMessage write(Player player, ByteBuf buffer) {
        ByteMessage msg = ByteMessage.message(135, MessageType.VAR, buffer);
        msg.putLong(name);
        msg.putInt(player.newPrivateMessageId());
        msg.put(rights);
        msg.putBytes(message);
        return msg;
    }
}
