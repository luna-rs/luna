package io.luna.net;

import io.luna.LunaContext;
import io.luna.net.codec.login.LoginDecoder;
import io.luna.net.session.Session;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.timeout.ReadTimeoutHandler;

/**
 * The {@link ChannelInitializer} implementation that will initialize
 * {@link SocketChannel}s before they are registered.
 *
 * @author lare96 <http://github.com/lare96>
 */
@Sharable
public final class LunaChannelInitializer extends ChannelInitializer<SocketChannel> {

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

        ch.pipeline().addLast("channel-filter", LunaChannelHandlers.CHANNEL_FILTER);
        ch.pipeline().addLast("login-decoder", new LoginDecoder(context));
        ch.pipeline().addLast("login-encoder", LunaChannelHandlers.LOGIN_ENCODER);
        ch.pipeline().addLast("upstream-handler", LunaChannelHandlers.UPSTREAM_HANDLER);
        ch.pipeline().addLast("read-timeout", new ReadTimeoutHandler(LunaNetworkConstants.READ_IDLE_SECONDS));
    }
}
