package io.luna.net.client;

import io.luna.game.model.mob.Player;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.GameMessageReader;
import io.luna.net.msg.GameMessageRepository;
import io.luna.net.msg.GameMessageWriter;
import io.netty.channel.Channel;

import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;

/**
 * A {@link Client} implementation model representing post-login I/O communications.
 *
 * @author lare96 <http://github.com/lare96>
 */
public class GameClient extends Client<GameMessage> {

    /**
     * The decoded packets.
     */
    private final Queue<GameMessage> decodedMessages = new ArrayBlockingQueue<>(15);

    /**
     * The message repository.
     */
    private final GameMessageRepository repository;

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
    void onMessageReceived(GameMessage msg) {
        if (!decodedMessages.offer(msg)) {
           msg.getPayload().releaseAll();
        }
    }

    /**
     * Handles decoded game packets and posts their created events to all applicable plugin listeners.
     * Fires a region update afterwards, if needed.
     */
    public void handleDecodedMessages(Player player) {
        for (; ; ) {
            var msg = decodedMessages.poll();
            if (msg == null) {
                break;
            }
            GameMessageReader reader = repository.get(msg.getOpcode());
            reader.postEvent(player, msg);
        }
        player.sendRegionUpdate();
    }

    /**
     * Enqueues an encoded message to the underlying channel; The channel is not flushed until the
     * end of the current game cycle.
     *
     * @param msg The message to queue.
     */
    public void queue(GameMessageWriter msg, Player player) {
        if (channel.isActive()) {
            channel.write(msg.toGameMsg(player), channel.voidPromise());
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
}