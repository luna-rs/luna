package io.luna.game.model.mob.bot;

import io.luna.net.msg.GameMessageWriter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A model handling data for input messages received by bots from this server. Messages can be cleared and sorted by
 * type and time.
 *
 * @author lare96
 */
public final class BotInputMessageHandler {

    /**
     * The game messages received from the server.
     */
    private final List<BotMessage<?>> receivedMessages = new ArrayList<>(1024);

    /**
     * Adds a flushed message from the server to the backing list.
     *
     * @param msg The message to add.
     */
    void add(BotMessage<?> msg) {
        receivedMessages.add(msg);
    }

    /**
     * Clears all received messages.
     */
    public void clear() {
        receivedMessages.clear();
    }

    /**
     * Retrieves all messages sent by this server to the {@link BotClient} since the last call to
     * {@link #clear()}, and received after {@code since}.
     */
    public List<BotMessage<?>> getAll(Instant since) {
        List<BotMessage<?>> filtered = new ArrayList<>();
        for (BotMessage<?> msg : receivedMessages) {
            Instant timestamp = msg.getTimestamp();
            if (timestamp.equals(since) || timestamp.isAfter(since)) {
                filtered.add(msg);
            }
        }
        return filtered;
    }

    /**
     * Retrieves all messages sent by this server to the {@link BotClient} since the last call to
     * {@link #clear()}, and received after {@code since}.
     */
    public <T extends GameMessageWriter> List<BotMessage<T>> getAll(Class<T> type) {
        List<BotMessage<T>> filtered = new ArrayList<>();
        for (BotMessage<?> msg : receivedMessages) {
            if (type.isAssignableFrom(msg.getMessage().getClass())) {
                filtered.add((BotMessage<T>) msg);
            }
        }
        return filtered;
    }

    /**
     * Retrieves all messages sent by this server to the {@link BotClient} since the last call to
     * {@link #clear()}, and received after {@code since}.
     */
    public <T extends GameMessageWriter> List<BotMessage<T>> getAll(Instant since, Class<?> type) {
        List<BotMessage<T>> filtered = new ArrayList<>();
        for (BotMessage<?> msg : receivedMessages) {
            Instant timestamp = msg.getTimestamp();
            if ((timestamp.equals(since) || timestamp.isAfter(since)) &&
                    type.isAssignableFrom(msg.getMessage().getClass())) {
                filtered.add((BotMessage<T>) msg);
            }
        }
        return filtered;
    }

    /**
     * Retrieves all messages sent by this server since the last call to {@link #clear()}. The returned
     * list is <strong>read-only</strong>.
     */
    public List<BotMessage<?>> getAll() {
        return Collections.unmodifiableList(receivedMessages);
    }
}
