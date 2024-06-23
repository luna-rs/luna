package io.luna.net.client;

import io.luna.game.model.mob.Player;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.GameMessageReader;
import io.luna.net.msg.GameMessageRepository;
import io.luna.net.msg.GameMessageWriter;
import io.netty.channel.Channel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;

/**
 * A {@link Client} implementation model representing post-login I/O communications.
 *
 * @author lare96 
 */
public class GameClient extends Client<GameMessage> {

    /**
     * The logger.
     */
    private static final Logger logger = LogManager.getLogger();

    /**
     * The decoded packets.
     */
    protected final Queue<GameMessage> pendingReadMessages = new ArrayBlockingQueue<>(15);

    /**
     * The message repository.
     */
    protected final GameMessageRepository repository;

    /**
     * If the client is awaiting logout.
     */
    private volatile boolean pendingLogout;

    /**
     * Creates a new {@link GameClient}.
     *
     * @param channel The client's channel.
     * @param repository The message repository.
     */
    public GameClient(Channel channel, GameMessageRepository repository) {
        super(channel);
        this.repository = repository;
    }

    @Override
    public void onInactive() {
        setPendingLogout(true);
    }

    @Override
   public  void onMessageReceived(GameMessage msg) {
        if (!pendingReadMessages.offer(msg)) {
            msg.getPayload().releaseAll();
        }
    }

    /**
     * Handles decoded game packets and posts their created events to all applicable plugin listeners.
     */
    public void handleDecodedMessages(Player player) {
        for (; ; ) {
            GameMessage msg = pendingReadMessages.poll();
            if (msg == null) {
                break;
            }
            GameMessageReader<?> reader = repository.get(msg.getOpcode());
            if (reader == null) {
                logger.warn("No assigned reader for opcode {}, size {}", msg.getOpcode(), msg.getSize());
                continue;
            }
            reader.submitMessage(player, msg);
        }
    }

    /**
     * Enqueues an encoded message to the underlying channel; The channel is not flushed until the
     * end of the current game cycle.
     *
     * @param msg The message to queue.
     */
    public void queue(GameMessageWriter msg, Player player) {
        if (channel.isActive()) {
            channel.write(msg.toGameMessage(player), channel.voidPromise());
        }
    }

    /**
     * Flushes the underlying channel. This will send all messages to the client queued using
     * {@link #queue(GameMessageWriter, Player)}. Calls to this method are expensive and should be done sparingly.
     */
    public void flush() {
        if (channel.isActive()) {
            channel.flush();
        }
    }

    /**
     * Sets if the client is awaiting logout.
     */
    public void setPendingLogout(boolean pendingLogout) {
        this.pendingLogout = pendingLogout;
    }

    /**
     * @return {@code true} if the client is awaiting logout.
     */
    public boolean isPendingLogout() {
        return pendingLogout;
    }

    /**
     * @return The game message repository.
     */
    public GameMessageRepository getRepository() {
        return repository;
    }
}