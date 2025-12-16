package io.luna.net.client;

import io.luna.game.LogoutService;
import io.luna.game.LogoutService.LogoutRequest;
import io.luna.game.model.mob.Player;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.GameMessageReader;
import io.luna.net.msg.GameMessageRepository;
import io.luna.net.msg.GameMessageWriter;
import io.netty.channel.Channel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Represents an active, post-login client connection responsible for encoding, decoding,
 * and queuing game messages between the server and a logged-in player.
 * <p>
 * Each {@link GameClient} instance is bound to a single {@link Player} and operates on a
 * dedicated {@link Channel} after successful login. Incoming messages are decoded and queued
 * into {@link #pendingReadMessages}, while outgoing messages are encoded and stored in
 * {@link #pendingWriteMessages} until the next flush cycle.
 * </p>
 *
 * <p>
 * This class provides thread-safe message queuing and controlled flushing at the end of each
 * game tick. It also integrates with the {@link LogoutService} to ensure a clean player logout
 * sequence when the client disconnects.
 * </p>
 *
 * @author lare96
 */
public class GameClient extends Client<GameMessage> {

    /**
     * The maximum number of incoming messages that can be processed in a single game cycle.
     * <p>
     * This hard limit prevents potential abuse from excessive packet spam or malformed clients
     * attempting to saturate the message queue.
     * </p>
     */
    private static final int MAX_READ_MESSAGES = 100;

    /**
     * The logger.
     */
    private static final Logger logger = LogManager.getLogger();

    /**
     * A queue of decoded messages awaiting processing.
     */
    protected final Queue<GameMessage> pendingReadMessages = new ConcurrentLinkedQueue<>();

    /**
     * A queue of encoded messages awaiting transmission to the client. Used to prevent pooled buffer leaks.
     */
    protected final Queue<GameMessage> pendingWriteMessages = new ConcurrentLinkedQueue<>();

    /**
     * The message repository that maps opcodes to their corresponding {@link GameMessageReader}.
     */
    protected final GameMessageRepository repository;

    /**
     * Indicates whether this client is currently pending logout.
     * <p>
     * This flag ensures logout requests are submitted only once per player.
     * </p>
     */
    private final AtomicBoolean pendingLogout = new AtomicBoolean();

    /**
     * The player instance.
     */
    private final Player player;

    /**
     * If the next logout should be forced (will logout while in combat, etc).
     */
    private volatile boolean forcedLogout;

    /**
     * Creates a new {@link GameClient} bound to the given network channel and player.
     *
     * @param channel The Netty channel for this connection.
     * @param repository The message repository used to decode incoming packets.
     * @param player The player instance associated with this client.
     */
    public GameClient(Channel channel, GameMessageRepository repository, Player player) {
        super(channel);
        this.repository = repository;
        this.player = player;
    }

    @Override
    public void onInactive() {
        sendLogoutRequest();
    }

    @Override
    public void onMessageReceived(GameMessage msg) {
        pendingReadMessages.add(msg);
    }

    /**
     * Processes all queued decoded messages up to {@link #MAX_READ_MESSAGES} per tick.
     * <p>
     * For each message, retrieves the associated {@link GameMessageReader} from the
     * {@link #repository}, and delegates message handling to the appropriate plugin or system
     * listener. Any unrecognized opcodes are logged as warnings.
     * </p>
     *
     * <p>
     * Each message is released after processing to return its underlying buffer to Netty’s
     * reference pool.
     * </p>
     */
    public void handleDecodedMessages() {
        int processed = 0;
        GameMessage msg;
        while ((msg = pendingReadMessages.poll()) != null && processed++ < MAX_READ_MESSAGES) {
            try {
                GameMessageReader<?> reader = repository.get(msg.getOpcode());
                if (reader == null) {
                    logger.warn("No assigned reader for opcode {}, size {}", msg.getOpcode(), msg.getSize());
                    continue;
                }
                reader.submitMessage(player, msg);
            } catch (Exception e) {
                logger.error("Error reading packet {}.", msg.getOpcode(), e);
            } finally {
                msg.getPayload().releaseAll();
                processed++;
            }
        }
    }

    /**
     * Queues an outgoing message for transmission to the client.
     * <p>
     * The message is built using the specified {@link GameMessageWriter} and written to the
     * channel asynchronously. Messages are not flushed immediately but will be sent collectively
     * when {@link #flush()} is called at the end of the cycle.
     * </p>
     *
     * @param writer The writer responsible for building the message to send.
     */
    public void queue(GameMessageWriter writer) {
        GameMessage msg = writer.toGameMessage(player);
        if (msg == null) {
            return;
        }
        if (channel.isActive()) {
            channel.eventLoop().execute(() -> {
                channel.write(msg, channel.voidPromise());
                if (msg.getPayload().refCnt() > 0) {
                    pendingWriteMessages.add(msg);
                }
            });
        } else {
            msg.getPayload().releaseAll();
        }
    }

    /**
     * Releases all pending write messages without sending them.
     * <p>
     * This is typically used when the channel has closed before a flush operation
     * could complete, to prevent memory leaks.
     * </p>
     */
    public void releasePendingWrites() {
        for (; ; ) {
            GameMessage msg = pendingWriteMessages.poll();
            if (msg == null) {
                break;
            }
            if (msg.getPayload().refCnt() > 0) {
                msg.getPayload().releaseAll();
            }
        }
    }

    /**
     * Flushes all queued messages to the client immediately.
     * <p>
     * This method should be called sparingly, as it triggers a full I/O flush on the underlying Netty channel.
     * Normally invoked once per game cycle.
     * </p>
     * <p>
     * If the channel is inactive, all pending messages are released instead.
     * </p>
     */
    public void flush() {
        if (channel.isActive()) {
            channel.eventLoop().submit(() -> {
                channel.flush();
                releasePendingWrites();
            });
        } else {
            releasePendingWrites();
        }
    }

    /**
     * Sends a logout request for this player to the {@link LogoutService}.
     * <p>
     * The request is submitted only once, even if multiple disconnects or errors occur.
     * </p>
     */
    public void sendLogoutRequest() {
        if (pendingLogout.compareAndSet(false, true)) {
            LogoutService logoutService = player.getWorld().getLogoutService();
            logoutService.submit(player.getUsername(), new LogoutRequest(player));
        }
    }

    /**
     * @return {@code true} if logout has been requested, otherwise {@code false}.
     */
    public boolean isPendingLogout() {
        return pendingLogout.get();
    }

    /**
     * @return If the next logout should be forced (will logout while in combat, etc).
     */
    public boolean isForcedLogout() {
        return forcedLogout;
    }

    /**
     * Sets if the next logout should be forced.
     *
     * @param forcedLogout The new value.
     */
    public void setForcedLogout(boolean forcedLogout) {
        this.forcedLogout = forcedLogout;
    }

    /**
     * @return The game message repository.
     */
    public GameMessageRepository getRepository() {
        return repository;
    }
}