package io.luna.net;

import io.luna.net.codec.login.LoginResponse;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import java.net.InetSocketAddress;

import com.google.common.collect.ConcurrentHashMultiset;
import com.google.common.collect.Multiset;

/**
 * The {@link io.netty.channel.ChannelInboundHandlerAdapter} implementation that
 * filters {@linkplain io.netty.channel.Channel channels} by the amount of
 * active connections they already have. A threshold is put on the amount of
 * successful connections allowed to be made in order to provide security from
 * socket flooder attacks.
 * <p>
 * <p>
 * This is required because Netty {@code 4.0.x} does not contain an
 * {@code ipfilter} package.
 * <p>
 * <p>
 * <strong>One {@code LunaChannelFilter} instance must be shared across all
 * pipelines in order to ensure that every channel is using the same
 * map.</strong>
 * 
 * @author lare96 <http://github.org/lare96>
 */
@Sharable
public class LunaChannelFilter extends ChannelInboundHandlerAdapter {

    /**
     * A concurrent {@link com.google.common.collect.Multiset} that holds the
     * amount of connections made by all active hosts.
     */
    private final Multiset<String> connections = ConcurrentHashMultiset.create();

    /**
     * The maximum amount of connections that can be made by a single host.
     */
    private final int connectionLimit;

    /**
     * Creates a new {@link io.luna.net.LunaChannelFilter}.
     *
     * @param connectionLimit
     *            The maximum amount of connections that can be made by a single
     *            host.
     */
    public LunaChannelFilter(int connectionLimit) {
        this.connectionLimit = connectionLimit;
    }

    /**
     * Creates a new {@link io.luna.net.LunaChannelFilter} with a connection
     * limit of {@code CONNECTION_LIMIT}.
     */
    public LunaChannelFilter() {
        this(LunaNetworkConstants.CONNECTION_LIMIT);
    }

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        String hostAddress = getAddress(ctx);
        if (hostAddress.equals("127.0.0.1")) {
            return;
        }
        if (connections.count(hostAddress) >= connectionLimit) {
            ctx.channel().writeAndFlush(LoginResponse.LOGIN_LIMIT_EXCEEDED);
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
     * Converts {@code ctx} to a {@code String} representation of the host
     * address.
     * 
     * @param ctx
     *            The channel handler context.
     * @return The {@code String} address representation.
     */
    private String getAddress(ChannelHandlerContext ctx) {
        return ((InetSocketAddress) ctx.channel().remoteAddress()).getAddress().getHostAddress();
    }
}
