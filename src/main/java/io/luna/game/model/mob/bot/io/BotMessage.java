package io.luna.game.model.mob.bot.io;

import io.luna.net.msg.GameMessageWriter;

import java.time.Instant;

/**
 * An incoming {@link GameMessageWriter} sent from this server to a {@link BotClient}.
 *
 * @author lare96
 */
public class BotMessage<T extends GameMessageWriter> {

    /**
     * The incoming message.
     */
    private final T message;

    /**
     * When this message was sent.
     */
    private final Instant timestamp;

    /**
     * Creates a new {@link BotMessage}.
     *
     * @param message The incoming message
     * @param timestamp When this message was sent.
     */
    public BotMessage(T message, Instant timestamp) {
        this.message = message;
        this.timestamp = timestamp;
    }

    /**
     * @return The incoming message
     */
    public T getMessage() {
        return message;
    }

    /**
     * @return When this message was sent.
     */
    public Instant getTimestamp() {
        return timestamp;
    }
}
