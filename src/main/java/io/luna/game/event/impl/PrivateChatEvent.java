package io.luna.game.event.impl;

import io.luna.game.model.mob.Player;
import io.luna.util.StringUtils;

/**
 * An event sent when a player sends a private message.
 *
 * @author lare96
 */
public final class PrivateChatEvent extends PlayerEvent implements ControllableEvent {

    /**
     * The name of the receiver.
     */
    private final long name;

    /**
     * The message to send.
     */
    private final byte[] message;

    /**
     * The unpacked message.
     */
    private final String unpackedMessage;

    /**
     * Creates a new {@link PrivateChatEvent}.
     *
     * @param player  The player.
     * @param name    The name of the receiver.
     * @param message The message to send.
     */
    public PrivateChatEvent(Player player, long name, byte[] message) {
        super(player);
        this.name = name;
        this.message = message;
        unpackedMessage = StringUtils.unpackText(message);
    }

    /**
     * @return The name of the receiver.
     */
    public long getName() {
        return name;
    }

    /**
     * @return The message to send.
     */
    public byte[] getMessage() {
        return message;
    }

    /**
     * @return The unpacked message.
     */
    public String getUnpackedMessage() {
        return unpackedMessage;
    }
}