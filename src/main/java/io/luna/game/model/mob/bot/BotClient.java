package io.luna.game.model.mob.bot;

import io.luna.game.model.mob.Player;
import io.luna.net.client.GameClient;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.GameMessageRepository;
import io.luna.net.msg.GameMessageWriter;

import java.time.Instant;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * A {@link GameClient} implementation that enables {@link Bot} types to perform artificial networking. All
 * bot IO can be processed and interpreted; for instance, bots can retrieve messages sent by the server and send
 * messages as if they were actual clients.
 * <p>
 * As a result of networking being artificial, there is zero latency when sending/receiving messages.
 *
 * @author lare96
 */
public final class BotClient extends GameClient {

    /**
     * The IO message handler.
     */
    private final BotMessageHandler messageHandler;

    /**
     * All pending write messages, completed when {@link #flush()} is called.
     */
    private final Queue<BotMessage> pendingWriteMessages = new ConcurrentLinkedQueue<>();

    /**
     * The bot instance.
     */
    private final Bot bot;

    /**
     * Creates a new {@link BotClient}.
     *
     * @param repository The message repository.
     */
    public BotClient(Bot bot, GameMessageRepository repository) {
        super(BotChannel.CHANNEL, repository);
        this.bot = bot;
        messageHandler = new BotMessageHandler(this, bot);
    }

    @Override
    public void onMessageReceived(GameMessage msg) {
        /* Shouldn't ever be called. The equivalent to this method for a bot is "flush()" since we're receiving all
        server messages sent (just not from Netty like here). */
        throw new IllegalStateException("Unexpected: " + bot.getUsername() + " calling onMessageReceived.");
    }

    @Override
    public void queue(GameMessageWriter msg, Player player) {
        // The server is trying to send a message to the client, since we're a bot the 'client' doesn't exist.
        // So we cache the messages, and they can be interpreted by scripts when needed.
        // eg. if(msg instanceof GameChatboxMessageWriter && msg.getMessage().equals("Welcome to Luna!")) ...
        if (!player.isBot()) {
            throw new IllegalStateException("Unexpected: " + player.getUsername() + " not a bot, but using BotClient.");
        }
        Instant timestamp = Instant.now();
        pendingWriteMessages.add(new BotMessage(msg, timestamp));
    }

    @Override
    public void flush() {
        // Send messages to the client, which is our bot!
        for (; ; ) {
            BotMessage writer = pendingWriteMessages.poll();
            if (writer == null) {
                break;
            }
            messageHandler.addMessage(writer);
        }
    }

    /**
     * Queues a {@link GameMessage} to be simulated in this server as an incoming packet from a client.
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
     * @return The IO message handler.
     */
    public BotMessageHandler getMessageHandler() {
        return messageHandler;
    }
}
