package io.luna.net.session;

import io.luna.game.model.World;
import io.luna.game.model.mob.Player;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.MessageReader;
import io.luna.net.msg.MessageRepository;
import io.luna.net.msg.MessageWriter;
import io.netty.channel.Channel;

import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.function.Consumer;

/**
 * A {@link Client} implementation model representing post-login I/O communications.
 *
 * @author lare96 <http://github.com/lare96>
 */
public class GameClient extends Client<GameMessage> {

    /**
     * The maximum amount of incoming packets handled per cycle.
     */
    private static final int MAX_MESSAGES = 15;

    /**
     * The player.
     */
    private final Player player;

    /**
     * The decoded packets.
     */
    private final Queue<GameMessage> decodedMessages = new ArrayBlockingQueue<>(MAX_MESSAGES);

    /**
     * The message repository.
     */
    private final MessageRepository repository;

    /**
     * Creates a new {@link GameClient}.
     *
     * @param channel The client's channel.
     * @param player The player.
     * @param repository The message repository.
     */
    public GameClient(Channel channel, Player player, MessageRepository repository) {
        super(channel);
        this.player = player;
        this.repository = repository;
    }

    @Override
    public void onInactive() {
        World world = player.getWorld();
        world.queueLogout(player);
    }

    @Override
    void onMessageReceived(GameMessage msg) {
        decodedMessages.offer(msg);
    }

    /**
     * Handles decoded game packets and posts their created events to all applicable plugin
     * listeners.
     */
    public void handleDecodedMessages() {
        for (; ; ) {
            GameMessage msg = decodedMessages.poll();
            if (msg == null) {
                break;
            }
            MessageReader handler = repository.getHandler(msg.getOpcode());
            handler.handleInboundMessage(player, msg);
        }
    }

    /**
     * Enqueues an encoded message to the underlying channel; The channel is not flushed until the
     * end of the current game cycle.
     *
     * @param msg The message to queue.
     */
    public void queue(MessageWriter msg) {
        Channel channel = getChannel();

        if (channel.isActive()) {
            Consumer<GameMessage> writeMsg = it -> channel.write(it, channel.voidPromise());
            msg.handleOutboundMessage(player).ifPresent(writeMsg);
        }
    }

    /**
     * Flushes the underlying channel. This will send all messages to the client queued using
     * {@link #queue(MessageWriter)}. Calls to this method are expensive and should be done sparingly (at
     * most once or twice per game cycle).
     */
    public void flush() {
        Channel channel = getChannel();

        if (channel.isActive()) {
            channel.flush();
        }
    }
}