package io.luna.net;

import io.luna.LunaContext;
import io.luna.net.codec.login.LoginDecoder;
import io.luna.net.codec.login.LoginEncoder;
import io.luna.net.session.Session;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.timeout.ReadTimeoutHandler;

/**
 * The {@link ChannelInitializer} implementation that will initialize {@link SocketChannel}s before they are registered.
 *
 * @author lare96 <http://github.com/lare96>
 */
@Sharable public final class LunaChannelInitializer extends ChannelInitializer<SocketChannel> {

    /**
     * Handles upstream messages from Netty.
     */
    private static final ChannelHandler UPSTREAM_HANDLER = new LunaUpstreamHandler();

    /**
     * Encodes the login response.
     */
    private static final ChannelHandler LOGIN_ENCODER = new LoginEncoder();

    /**
     * Filters channels based on the amount of active connections they have.
     */
    public static final ChannelHandler CHANNEL_FILTER = new LunaChannelFilter();

    /**
     * The underlying context to be managed under.
     */
    private final LunaContext context;

    /**
     * Creates a new {@link LunaChannelInitializer}.
     *
     * @param context The underlying context to be managed under.
     */
    public LunaChannelInitializer(LunaContext context) {
        this.context = context;
    }

    @Override
    protected void initChannel(SocketChannel ch) throws Exception {
        ch.attr(LunaNetworkConstants.SESSION_KEY).setIfAbsent(new Session(ch));

        ch.pipeline().addLast("channel-filter", CHANNEL_FILTER);
        ch.pipeline().addLast("login-decoder", new LoginDecoder(context));
        ch.pipeline().addLast("login-encoder", LOGIN_ENCODER);
        ch.pipeline().addLast("read-timeout", new ReadTimeoutHandler(LunaNetworkConstants.READ_IDLE_SECONDS));
        ch.pipeline().addLast("upstream-handler", UPSTREAM_HANDLER);
    }
}
