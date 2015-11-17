package io.luna.net;

import com.google.common.collect.ConcurrentHashMultiset;
import com.google.common.collect.Multiset;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.login.LoginResponse;
import io.luna.net.codec.login.LoginResponseMessage;
import io.luna.util.parser.impl.IpBanParser;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import java.net.InetSocketAddress;

/**
 * A {@link ChannelInboundHandlerAdapter} implementation that filters
 * {@link Channel}s by the amount of active connections they already have. A
 * threshold is put on the amount of successful connections allowed to be made
 * in order to provide security from socket flooder attacks.
 * <p>
 * <p>
 * <strong>One {@code LunaChannelFilter} instance must be shared across all
 * pipelines in order to ensure that every channel is using the same
 * multiset.</strong>
 * 
 * @author lare96 <http://github.org/lare96>
 */
@Sharable
public final class LunaChannelFilter extends ChannelInboundHandlerAdapter {

    /**
     * A concurrent {@link Multiset} that holds the amount of connections made
     * by all active hosts.
     */
    private final Multiset<String> connections = ConcurrentHashMultiset.create();

    /**
     * The maximum amount of connections that can be made by a single host.
     */
    private final int connectionLimit;

    /**
     * Creates a new {@link LunaChannelFilter} with a connection limit of
     * {@code CONNECTION_LIMIT}.
     */
    public LunaChannelFilter() {
        connectionLimit = LunaNetworkConstants.CONNECTION_LIMIT;
    }

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        String hostAddress = getAddress(ctx);
        if (hostAddress.equals("127.0.0.1")) {
            return;
        }
        if (connections.count(hostAddress) >= connectionLimit) {
            disconnect(ctx, LoginResponse.LOGIN_LIMIT_EXCEEDED);
            return;
        }
        if (IpBanParser.BANNED_ADDRESSES.contains(hostAddress)) {
            disconnect(ctx, LoginResponse.ACCOUNT_DISABLED);
            return;
        }
        connections.add(hostAddress);
        ctx.fireChannelRegistered();
    }

    @Override
    public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
        String hostAddress = getAddress(ctx);
        if (hostAddress.equals("127.0.0.1")) {
            return;
        }
        connections.remove(hostAddress);
        ctx.fireChannelUnregistered();
    }

    /**
     * Disconnects {@code ctx} with {@code response} as the response code.
     * 
     * @param ctx The channel handler context.
     * @param response The response to disconnect with.
     */
    private void disconnect(ChannelHandlerContext ctx, LoginResponse response) {
        LoginResponseMessage message = new LoginResponseMessage(LoginResponse.LOGIN_LIMIT_EXCEEDED);
        ByteBuf initialMessage = ByteMessage.create(8).putLong(0).getBuffer();

        ctx.channel().write(initialMessage, ctx.channel().voidPromise());
        ctx.channel().writeAndFlush(message).addListener(ChannelFutureListener.CLOSE);
    }

    /**
     * Converts {@code ctx} to a {@code String} representation of the host
     * address.
     * 
     * @param ctx The channel handler context.
     * @return The {@code String} address representation.
     */
    private String getAddress(ChannelHandlerContext ctx) {
        return ((InetSocketAddress) ctx.channel().remoteAddress()).getAddress().getHostAddress();
    }
}
