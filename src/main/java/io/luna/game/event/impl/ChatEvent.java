package io.luna.game.event.impl;

import io.luna.game.model.mob.Player;

/**
 * An event sent when a player talks.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class ChatEvent extends PlayerEvent {

    /**
     * The chat effects.
     */
    private final int effects;

    /**
     * The chat color.
     */
    private final int color;

    /**
     * The message length.
     */
    private final int messageLength;

    /**
     * The message.
     */
    private final byte[] message;

    /**
     * Creates a new {@link ChatEvent}.
     *
     * @param player The player.
     * @param effects The chat effects.
     * @param color The chat color.
     * @param messageLength The message length.
     * @param message The message.
     */
    public ChatEvent(Player player, int effects, int color, int messageLength, byte[] message) {
        super(player);
        this.effects = effects;
        this.color = color;
        this.messageLength = messageLength;
        this.message = message;
    }

    /**
     * @return The chat effects.
     */
    public int getEffects() {
        return effects;
    }

    /**
     * @return The chat color.
     */
    public int getColor() {
        return color;
    }

    /**
     * @return The message length.
     */
    public int getMessageLength() {
        return messageLength;
    }

    /**
     * @return The message.
     */
    public byte[] getMessage() {
        return message;
    }
}
