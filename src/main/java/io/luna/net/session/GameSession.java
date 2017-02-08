package io.luna.net.session;

import io.luna.game.model.mobile.Player;
import io.luna.net.LunaNetworkConstants;
import io.luna.net.codec.IsaacCipher;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.MessageReader;
import io.luna.net.msg.MessageRepository;
import io.luna.net.msg.MessageWriter;
import io.netty.channel.Channel;

import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;

/**
 * A {@link Session} implementation that handles gameplay networking.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class GameSession extends Session {

    /**
     * The player.
     */
    private final Player player;

    /**
     * The encryptor.
     */
    private final IsaacCipher encryptor;

    /**
     * The decryptor.
     */
    private final IsaacCipher decryptor;

    /**
     * The message repository.
     */
    private final MessageRepository messageRepository;

    /**
     * A bounded queue of decoded game packets.
     */
    private final Queue<GameMessage> inboundQueue = new ArrayBlockingQueue<>(LunaNetworkConstants.MESSAGE_LIMIT);

    /**
     * Creates a new {@link GameSession}.
     *
     * @param channel The client's channel.
     * @param encryptor The encryptor.
     * @param decryptor The decryptor.
     * @param messageRepository The message repository.
     */
    public GameSession(Player player, Channel channel, IsaacCipher encryptor, IsaacCipher decryptor,
        MessageRepository messageRepository) {
        super(channel);
        this.player = player;
        this.encryptor = encryptor;
        this.decryptor = decryptor;
        this.messageRepository = messageRepository;
    }

    @Override
    public void onDispose() {
        player.getWorld().queueLogout(player);
    }

    @Override
    public void handleUpstreamMessage(Object msg) {
        if (msg instanceof GameMessage) {
            inboundQueue.offer((GameMessage) msg);
        }
    }

    /**
     * Writes a message to the underlying channel; The channel is not flushed.
     */
    public void queue(MessageWriter msg) {
        Channel channel = getChannel();

        if (channel.isActive()) {
            channel.write(msg.handleOutboundMessage(player), channel.voidPromise());
        }
    }

    /**
     * Flushes the underlying channel.
     */
    public void flush() {
        Channel channel = getChannel();

        if (channel.isActive()) {
            channel.flush();
        }
    }

    /**
     * Dequeues decoded game packets and applies their listeners to them.
     */
    public void dequeue() {
        for (; ; ) {
            GameMessage msg = inboundQueue.poll();
            if (msg == null) {
                break;
            }
            MessageReader inbound = messageRepository.getHandler(msg.getOpcode());
            inbound.handleInboundMessage(player, msg);
        }
    }

    /**
     * @return The encryptor.
     */
    public IsaacCipher getEncryptor() {
        return encryptor;
    }

    /**
     * @return The decryptor.
     */
    public IsaacCipher getDecryptor() {
        return decryptor;
    }
}
