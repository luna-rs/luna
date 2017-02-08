package io.luna.net;

import io.luna.LunaContext;
import io.luna.net.codec.login.LoginDecoder;
import io.luna.net.codec.login.LoginEncoder;
import io.luna.net.msg.MessageRepository;
import io.luna.net.session.Session;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.timeout.ReadTimeoutHandler;

/**
 * A {@link ChannelInitializer} implementation that will initialize {@link SocketChannel}s before they are
 * registered.
 *
 * @author lare96 <http://github.com/lare96>
 */
@Sharable public final class LunaChannelInitializer extends ChannelInitializer<SocketChannel> {

    /**
     * Handles upstream messages from Netty.
     */
    private final ChannelHandler upstreamHandler = new LunaUpstreamHandler();

    /**
     * Encodes the login response.
     */
    private final ChannelHandler loginEncoder = new LoginEncoder();

    /**
     * Filters channels based on their active connection count.
     */
    public final ChannelHandler channelFilter = new LunaChannelFilter();

    /**
     * The context instance.
     */
    private final LunaContext context;

    /**
     * The message repository.
     */
    private final MessageRepository messageRepository;

    /**
     * Creates a new {@link LunaChannelInitializer}.
     *
     * @param context The context instance.
     * @param messageRepository The message repository.
     */
    public LunaChannelInitializer(LunaContext context, MessageRepository messageRepository) {
        this.context = context;
        this.messageRepository = messageRepository;
    }

    @Override
    protected void initChannel(SocketChannel ch) throws Exception {
        ch.attr(LunaNetworkConstants.SESSION_KEY).setIfAbsent(new Session(ch));

        ch.pipeline().addLast("read-timeout", new ReadTimeoutHandler(LunaNetworkConstants.READ_IDLE_SECONDS));
        ch.pipeline().addLast("channel-filter", channelFilter);
        ch.pipeline().addLast("login-decoder", new LoginDecoder(context, messageRepository));
        ch.pipeline().addLast("login-encoder", loginEncoder);
        ch.pipeline().addLast("upstream-handler", upstreamHandler);
    }
}
