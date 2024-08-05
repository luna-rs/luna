package io.luna.game.event.impl;

import io.luna.game.model.mob.Player;

import java.time.Instant;

/**
 * An event sent when a player talks.
 *
 * @author lare96
 */
public final class ChatEvent extends PlayerEvent implements ControllableEvent {

    /**
     * The chat effect.
     */
    private final int effect;

    /**
     * The chat color.
     */
    private final int color;

    /**
     * The message length.
     */
    private final int messageLength;

    /**
     * The packed message.
     */
    private final byte[] message;

    /**
     * The unpacked message.
     */
    private final String unpackedMessage;

    /**
     * A timestamp of when it occurred.
     */
    private final Instant timestamp = Instant.now();


    /**
     * Creates a new {@link ChatEvent}.
     *
     * @param player The player.
     * @param effect The chat effects.
     * @param color The chat color.
     * @param messageLength The message length.
     * @param message The message.
     * @param unpackedMessage The unpacked message.
     */
    public ChatEvent(Player player, int effect, int color, int messageLength, byte[] message, String unpackedMessage) {
        super(player);
        this.effect = effect;
        this.color = color;
        this.messageLength = messageLength;
        this.message = message;
        this.unpackedMessage = unpackedMessage;
    }

    /**
     * @return The chat effects.
     */
    public int getEffect() {
        return effect;
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

    /**
     * @return The unpacked message.
     */
    public String getUnpackedMessage(){
        return unpackedMessage;
    }

    /**
     * @return The timestamp of the message.
     */
    public Instant getTimestamp() {
        return timestamp;
    }
}

