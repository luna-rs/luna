package io.luna.net;

import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.timeout.IdleStateHandler;

/**
 * The {@link io.netty.channel.ChannelInitializer} implementation that will
 * initialize {@linkplain io.netty.channel.socket.SocketChannel socket channels}
 * before they are registered.
 *
 * @author lare96 <http://github.com/lare96>
 */
@Sharable
public final class LunaChannelInitializer extends ChannelInitializer<SocketChannel> {

    @Override
    protected void initChannel(SocketChannel ch) throws Exception {
        ch.pipeline().addLast("login-handshake", LunaChannelHandlers.HANDSHAKE_DECODER);
        ch.pipeline().addLast("upstream-handler", LunaChannelHandlers.UPSTREAM_HANDLER);
        ch.pipeline().addLast("idle-state", new IdleStateHandler(LunaNetworkConstants.READ_IDLE_SECONDS, 0, 0));
    }
}
