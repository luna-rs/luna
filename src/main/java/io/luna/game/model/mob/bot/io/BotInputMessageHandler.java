package io.luna.game.model.mob.bot.io;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import io.luna.game.model.mob.bot.Bot;

/**
 * A model responsible for managing input messages received by a {@link Bot} from the server.
 *
 * @author lare96
 */
public final class BotInputMessageHandler {

    /**
     * A multimap of all messages received from the server, keyed by message class.
     */
    private final Multimap<Class<?>, BotMessage<?>> received = ArrayListMultimap.create();

    /**
     * The bot.
     */
    private final Bot bot;

    /**
     * Creates a new {@link BotInputMessageHandler}.
     *
     * @param bot The bot.
     */
    public BotInputMessageHandler(Bot bot) {
        this.bot = bot;
    }

    /**
     * Adds a newly flushed message from the server to the internal multimap.
     *
     * @param msg The message to add. Must not be {@code null}.
     */
    void add(BotMessage<?> msg) {
        received.put(msg.getClass(), msg);
    }

    /**
     * @return The internal multimap of received messages.
     */
    public Multimap<Class<?>, BotMessage<?>> getReceived() {
        return received;
    }

    /**
     * @return The bot.
     */
    public Bot getBot() {
        return bot;
    }
}
