package io.luna.net;

import io.luna.net.session.Session;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.timeout.ReadTimeoutHandler;

/**
 * The {@link io.netty.channel.ChannelInitializer} implementation that will
 * initialize {@linkplain io.netty.channel.socket.SocketChannel socket channels}
 * before they are registered.
 *
 * @author lare96 <http://github.com/lare96>
 */
@Sharable
public final class LunaChannelInitializer extends ChannelInitializer<SocketChannel> {

    /**
     * A default access level constructor to discourage external instantiation
     * outside of the {@code io.luna.net} package.
     */
    LunaChannelInitializer() {}

    @Override
    protected void initChannel(SocketChannel ch) throws Exception {
        ch.attr(LunaNetworkConstants.SESSION_KEY).setIfAbsent(new Session(ch));

        ch.pipeline().addLast("channel-filter", LunaChannelHandlers.CHANNEL_FILTER);
        ch.pipeline().addLast("login-decoder", LunaChannelHandlers.LOGIN_DECODER);
        ch.pipeline().addLast("upstream-handler", LunaChannelHandlers.UPSTREAM_HANDLER);
        ch.pipeline().addLast("read-timeout", new ReadTimeoutHandler(LunaNetworkConstants.READ_IDLE_SECONDS));
    }
}
