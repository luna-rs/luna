package io.luna.game.model.mob.bot.io;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import io.luna.game.model.mob.bot.Bot;

/**
 * Stores input messages received by a {@link Bot}.
 * <p>
 * This handler is used by bot logic to inspect messages that were sent from the server to the bot. Messages are
 * grouped by their concrete message class so scripts and handlers can quickly check for specific response types.
 *
 * @author lare96
 */
public final class BotInputMessageHandler {

    /**
     * The messages received by this bot, grouped by message class.
     */
    private final Multimap<Class<?>, BotMessage<?>> received = ArrayListMultimap.create();

    /**
     * The bot that owns this message handler.
     */
    private final Bot bot;

    /**
     * Creates a new input message handler for the specified bot.
     *
     * @param bot The owning bot.
     */
    public BotInputMessageHandler(Bot bot) {
        this.bot = bot;
    }

    /**
     * Records a message that was received from the server.
     * <p>
     * The message is stored under its concrete runtime class. Multiple messages of the same type may be stored.
     *
     * @param msg The received message.
     */
    void add(BotMessage<?> msg) { // todo Autoclear every 100 entries or so.
        received.put(msg.getClass(), msg);
    }

    /**
     * Removes all received messages from this handler.
     */
    public void clear() {
        received.clear();
    }

    /**
     * Returns all received messages grouped by message class.
     * <p>
     * The returned multimap is the live backing collection.
     *
     * @return The received message multimap.
     */
    public Multimap<Class<?>, BotMessage<?>> getReceived() {
        return received;
    }

    /**
     * Returns the bot that owns this message handler.
     *
     * @return The owning bot.
     */
    public Bot getBot() {
        return bot;
    }
}