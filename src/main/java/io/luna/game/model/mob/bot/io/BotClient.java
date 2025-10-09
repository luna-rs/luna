package io.luna.game.model.mob.bot.io;

import io.luna.game.model.mob.bot.Bot;
import io.luna.net.client.GameClient;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.GameMessageRepository;
import io.luna.net.msg.GameMessageWriter;

import java.time.Instant;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * A specialized {@link GameClient} implementation that simulates network communication for {@link Bot}
 * instances. Bot networking is entirely artificial and handled in-memory, making all I/O operations instantaneous.
 * <p>
 * The {@link BotClient} architecture mirrors a real client session:
 * <ul>
 *     <li>{@link #queue(GameMessageWriter)} simulates sending server messages to the bot.</li>
 *     <li>{@link #flush()} delivers all queued server messages to the bot’s {@link BotInputMessageHandler}.</li>
 *     <li>{@link #queueSimulated(GameMessage)} simulates incoming packets from the bot to the server.</li>
 * </ul>
 *
 * @author lare96
 */
public final class BotClient extends GameClient {

    /**
     * Handles incoming (server -> bot) messages.
     */
    private final BotInputMessageHandler input;

    /**
     * Handles outgoing (bot -> server) messages.
     */
    private final BotOutputMessageHandler output;

    /**
     * All pending messages awaiting delivery to the bot, finalized when {@link #flush()} is called.
     */
    private final Queue<BotMessage<?>> pendingWriteMessages = new ConcurrentLinkedQueue<>();

    /**
     * The bot.
     */
    private final Bot bot;

    /**
     * Creates a new {@link BotClient}.
     *
     * @param bot The bot.
     * @param repository The message repository.
     */
    public BotClient(Bot bot, GameMessageRepository repository) {
        super(BotChannel.CHANNEL, repository, bot);
        this.bot = bot;
        input = new BotInputMessageHandler(bot);
        output = new BotOutputMessageHandler(this);
    }

    /**
     * Should never be called, since bots don't receive packets through Netty.
     */
    @Override
    public void onMessageReceived(GameMessage msg) {
        throw new IllegalStateException("Unexpected: " + bot.getUsername() + " calling onMessageReceived.");
    }

    /**
     * Simulates a server -> client send.
     */
    @Override
    public void queue(GameMessageWriter msg) {
        Instant timestamp = Instant.now();
        pendingWriteMessages.add(new BotMessage<>(msg, timestamp));
    }

    /**
     * Transfers queued messages from the server to the bot’s input buffer.
     */
    @Override
    public void flush() {
        // Send messages to the client, which is our bot!
        for (; ; ) {
            BotMessage<?> writer = pendingWriteMessages.poll();
            if (writer == null) {
                break;
            }
            input.add(writer);
        }
    }

    /**
     * Queues a {@link GameMessage} to be simulated as an incoming packet from the bot to the server. This mimics
     * client-originated packets.
     *
     * @param msg The message to simulate.
     */
    public void queueSimulated(GameMessage msg) {
        pendingReadMessages.add(msg);
    }

    /**
     * @return The bot instance.
     */
    public Bot getBot() {
        return bot;
    }

    /**
     * @return Handles incoming (server -> bot) messages.
     */
    public BotInputMessageHandler getInput() {
        return input;
    }

    /**
     * @return Handles outgoing (bot -> server) messages.
     */
    public BotOutputMessageHandler getOutput() {
        return output;
    }
}
