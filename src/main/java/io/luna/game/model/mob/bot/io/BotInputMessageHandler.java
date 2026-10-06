package io.luna.game.model.mob.bot.io;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimap;
import io.luna.game.model.mob.bot.Bot;
import io.luna.net.msg.GameMessageWriter;

import java.util.List;

/**
 * Stores input messages received by a {@link Bot}.
 * <p>
 * This handler is used by bot logic to inspect messages that were sent from the server to the bot. Messages are
 * grouped by their concrete message class so scripts and handlers can quickly check for specific response types.
 *
 * @author lare96
 * @author TheLining
 */
public final class BotInputMessageHandler {

    /**
     * The most messages of a single type that are kept. Once a type is over this, its oldest message is dropped.
     */
    static final int MAX_MESSAGES_PER_TYPE = 100;

    /**
     * The messages received by this bot, grouped by message class.
     */
    private final ListMultimap<Class<?>, BotMessage<?>> received = ArrayListMultimap.create();

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
     * The message is stored under the class of its {@link GameMessageWriter}. Up to {@link #MAX_MESSAGES_PER_TYPE}
     * messages of the same type are kept, newest last.
     *
     * @param msg The received message.
     */
    void add(BotMessage<?> msg) {
        List<BotMessage<?>> messages = received.get(msg.getMessage().getClass());
        messages.add(msg);
        if (messages.size() > MAX_MESSAGES_PER_TYPE) {
            messages.removeFirst();
        }
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