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
     * The player.
     */
    private final Player player;

    /**
     * The decoded packets.
     */
    private final Queue<GameMessage> decodedMessages = new ArrayBlockingQueue<>(15);

    /**
     * The message repository.
     */
    private final GameMessageRepository repository;

    /**
     * Creates a new {@link GameClient}.
     *
     * @param channel The client's channel.
     * @param player The player.
     * @param repository The message repository.
     */
    public GameClient(Channel channel, Player player, GameMessageRepository repository) {
        super(channel);
        this.player = player;
        this.repository = repository;
    }

    @Override
    public void onInactive() {
        player.setPendingLogout(true);
    }

    @Override
    void onMessageReceived(GameMessage msg) {
        if (!decodedMessages.offer(msg)) {
            // Release buffer if unsuccessful or the memory will never get freed.
            var payload = msg.getPayload();
            payload.release(payload.refCnt());
        }
    }

    /**
     * Handles decoded game packets and posts their created events to all applicable plugin listeners.
     * Fires a region update afterwards, if needed.
     */
    public void handleDecodedMessages() {
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
    public void queue(GameMessageWriter msg) {
        if (channel.isActive()) {
            channel.write(msg.toGameMsg(player), channel.voidPromise());
        }
    }

    /**
     * Flushes the underlying channel. This will send all messages to the client queued using
     * {@link #queue(GameMessageWriter)}. Calls to this method are expensive and should be done sparingly.
     */
    public void flush() {
        if (channel.isActive()) {
            channel.flush();
        }
    }
}