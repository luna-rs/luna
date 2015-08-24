package io.luna.net;

import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;

import java.net.InetSocketAddress;

import com.google.common.collect.ConcurrentHashMultiset;
import com.google.common.collect.Multiset;

/**
 * The {@link io.netty.channel.ChannelHandlerAdapter} implementation that
 * filters {@linkplain io.netty.channel.Channel channels} by the amount of
 * active connections they already have. A threshold is put on the amount of
 * successful connections allowed to be made in order to provide security from
 * socket flooder attacks.
 * <p>
 * <p>
 * <strong>One {@code LunaChannelFilter} instance must be shared across all
 * pipelines in order to ensure that every channel is using the same
 * map.</strong>.
 * 
 * @author lare96 <http://github.org/lare96>
 */
@Sharable
public class LunaChannelFilter extends ChannelHandlerAdapter {

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
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        String hostAddress = getAddress(ctx);
        if (hostAddress.equals("127.0.0.1")) {
            return;
        }
        if (connections.count(hostAddress) >= connectionLimit) {
            ctx.channel().close();
            return;
        }
        connections.add(hostAddress);
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        String hostAddress = getAddress(ctx);
        if (hostAddress.equals("127.0.0.1")) {
            return;
        }
        connections.remove(hostAddress);
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
