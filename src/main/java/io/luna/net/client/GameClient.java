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
     * A queue of outgoing messages held back until the game encoder is installed on the channel. Only used while
     * {@link #holdingOutgoing} is {@code true}.
     */
    private final Queue<GameMessage> heldMessages = new ConcurrentLinkedQueue<>();

    /**
     * If outgoing messages are being held in {@link #heldMessages} instead of written to the channel. Set during the
     * final login handshake, when the channel pipeline can't yet encode {@link GameMessage} types.
     */
    private volatile boolean holdingOutgoing;

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
        while (processed++ < MAX_READ_MESSAGES && (msg = pendingReadMessages.poll()) != null) {
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
            }
        }
    }

    /**
     * Queues an outgoing message for transmission to the client.
     * <p>
     * The message is built using the specified {@link GameMessageWriter} and written to the channel asynchronously.
     * Messages are not flushed immediately but will be sent collectively when {@link #flush()} is called at the end of
     * the cycle.
     *
     * @param writer The writer responsible for building the message to send.
     */
    public void queue(GameMessageWriter writer) {
        GameMessage msg = writer.toGameMessage(player);
        if (msg == null) {
            return;
        }
        if (holdingOutgoing) {
            heldMessages.add(msg);
            return;
        }
        write(msg);
    }

    /**
     * Writes a built message to the channel on its event loop, releasing the payload if it can't be sent.
     *
     * @param msg The message to write.
     */
    private void write(GameMessage msg) {
        if (!channel.isActive()) {
            msg.getPayload().releaseAll();
            return;
        }
        channel.eventLoop().execute(() -> {
            if (channel.isActive()) {
                channel.write(msg).addListener(future -> {
                    // Netty won't release our payload if the write fails (e.g. no encoder for the message).
                    if (!future.isSuccess() && msg.getPayload().refCnt() > 0) {
                        msg.getPayload().releaseAll();
                    }
                });
            } else if (msg.getPayload().refCnt() > 0) {
                msg.getPayload().releaseAll();
            }
        });
    }

    /**
     * Starts holding outgoing messages instead of writing them. Used during the final login handshake, since messages
     * queued before the game encoder is installed can't be encoded. Must be paired with {@link #releaseHeldMessages()}.
     */
    public void holdOutgoingMessages() {
        holdingOutgoing = true;
    }

    /**
     * Stops holding outgoing messages and writes everything that was held, in order. Should be called once the game
     * encoder has been installed on the channel.
     */
    public void releaseHeldMessages() {
        holdingOutgoing = false;
        GameMessage msg;
        while ((msg = heldMessages.poll()) != null) {
            write(msg);
        }
    }

    /**
     * Releases all pending inbound messages without processing them.
     * <p>
     * This is typically used when the channel closes before pending messages can be processed, ensuring that retained
     * buffers do not cause memory leaks.
     */
    public void releasePendingMessages() {
        GameMessage msg;

        while ((msg = pendingReadMessages.poll()) != null) {
            if (msg.getPayload().refCnt() > 0) {
                msg.getPayload().releaseAll();
            }
        }
        while ((msg = heldMessages.poll()) != null) {
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
            channel.eventLoop().submit(channel::flush);
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