package io.luna.net.session;

import io.luna.game.model.mobile.Player;
import io.luna.net.LunaNetworkConstants;
import io.luna.net.codec.IsaacCipher;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.InboundGameMessage;
import io.luna.net.msg.OutboundGameMessage;
import io.netty.channel.Channel;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;

/**
 * A {@link Session} implementation that handles networking for a {@link Player} during gameplay.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class GameSession extends Session {

    /**
     * The logger that will print important information.
     */
    private static final Logger LOGGER = LogManager.getLogger(GameSession.class);

    /**
     * The player assigned to this {@code GameSession}.
     */
    private final Player player;

    /**
     * The message encryptor.
     */
    private final IsaacCipher encryptor;

    /**
     * The message decryptor.
     */
    private final IsaacCipher decryptor;

    /**
     * A bounded queue of inbound {@link GameMessage}s.
     */
    private final Queue<GameMessage> inboundQueue = new ArrayBlockingQueue<>(LunaNetworkConstants.MESSAGE_LIMIT);

    /**
     * Creates a new {@link GameSession}.
     *
     * @param channel The channel for this session.
     * @param encryptor The message encryptor.
     * @param decryptor The message decryptor.
     */
    public GameSession(Player player, Channel channel, IsaacCipher encryptor, IsaacCipher decryptor) {
        super(channel);
        this.player = player;
        this.encryptor = encryptor;
        this.decryptor = decryptor;
    }

    @Override
    public void onDispose() {
        player.getWorld().queueLogout(player);
        inboundQueue.clear();
    }

    @Override
    public void handleUpstreamMessage(Object msg) {
        if (msg instanceof GameMessage) {
            inboundQueue.offer((GameMessage) msg);
        }
    }

    /**
     * Writes {@code msg} to the underlying channel; The channel is not flushed.
     *
     * @param msg The message to queue.
     */
    public void queue(OutboundGameMessage msg) {
        Channel channel = getChannel();

        if (channel.isActive()) {
            channel.write(msg.toGameMessage(player), channel.voidPromise());
        }
    }

    /**
     * Dequeues the inbound queue, handling all logic accordingly.
     */
    public void dequeue() {
        for (; ; ) {
            GameMessage msg = inboundQueue.poll();
            if (msg == null) {
                break;
            }
            InboundGameMessage inbound = InboundGameMessage.HANDLERS[msg.getOpcode()];
            try {
                Object evt = inbound.readMessage(player, msg);
                if (evt != null) {
                    player.getPlugins().post(evt, player);
                }
            } catch (Exception e) {
                LOGGER.catching(Level.WARN, e);
            }
        }
    }

    /**
     * Flushes all of the {@link OutboundGameMessage}s in the underlying channel's queue.
     */
    public void flush() {
        Channel channel = getChannel();

        if (channel.isActive()) {
            channel.flush();
        }
    }

    /**
     * @return The message encryptor.
     */
    public IsaacCipher getEncryptor() {
        return encryptor;
    }

    /**
     * @return The message decryptor.
     */
    public IsaacCipher getDecryptor() {
        return decryptor;
    }
}
