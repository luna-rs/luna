package io.luna.net;

import com.google.common.collect.ConcurrentHashMultiset;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multiset;
import com.google.common.collect.Sets;
import io.luna.Luna;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.login.LoginResponse;
import io.luna.net.codec.login.LoginResponseMessage;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.ipfilter.AbstractRemoteAddressFilter;
import io.netty.util.AttributeKey;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Set;

/**
 * An {@link AbstractRemoteAddressFilter} implementation that filters {@link Channel}s by the amount of active
 * connections they already have and whether or not they are blacklisted. A threshold is put on the amount of
 * successful connections allowed to be made in order to provide security from socket flooder attacks.
 * <p>
 * <strong>One instance of this class must be shared across all pipelines in order to ensure that every
 * channel is using the same multiset.</strong>
 *
 * @author lare96 <http://github.org/lare96>
 */
@Sharable
public final class LunaChannelFilter extends AbstractRemoteAddressFilter<InetSocketAddress> {

    /**
     * An attribute key used to retrieve this channel filter.
     */
    public static final AttributeKey<LunaChannelFilter> KEY = AttributeKey.valueOf("LunaChannelFilter.key");

    /**
     * An immutable set containing whitelisted (filter bypassing) addresses.
     */
    public static final ImmutableSet<String> WHITELIST = ImmutableSet.of("127.0.0.1");

    /**
     * An attribute describing the login response for rejected channels.
     */
    private static final AttributeKey<LoginResponse> LOGIN_RESPONSE_KEY =
            AttributeKey.valueOf("LunaChannelFilter.loginResponseKey");

    /**
     * A concurrent multiset containing active connection counts.
     */
    private final Multiset<String> connections = ConcurrentHashMultiset.create();

    /**
     * A concurrent set containing blacklisted addresses.
     */
    private final Set<String> blacklist = Sets.newConcurrentHashSet();

    @Override
    protected boolean accept(ChannelHandlerContext ctx, InetSocketAddress remoteAddress) throws Exception {
        String address = ipAddress(remoteAddress);

        if (WHITELIST.contains(address)) {
            // Bypass filter for whitelisted addresses.
            return true;
        }
        if (connections.count(address) >= Luna.settings().connectionLimit()) {
            // Reject if more than CONNECTION_LIMIT active connections.
            response(ctx, LoginResponse.LOGIN_LIMIT_EXCEEDED);
            return false;
        }
        if (blacklist.contains(address)) {
            // Reject if blacklisted (IP banned).
            response(ctx, LoginResponse.ACCOUNT_BANNED);
            return false;
        }
        return true;
    }

    @Override
    protected void channelAccepted(ChannelHandlerContext ctx, InetSocketAddress remoteAddress) {
        String address = ipAddress(remoteAddress);

        // Increment connection count by 1.
        connections.add(address);

        // Remove address once disconnected.
        ChannelFuture future = ctx.channel().closeFuture();
        future.addListener(it -> connections.remove(address));
    }

    @Override
    protected ChannelFuture channelRejected(ChannelHandlerContext ctx, InetSocketAddress remoteAddress) {
        Channel channel = ctx.channel();

        // Retrieve the response message.
        LoginResponse response = channel.attr(LOGIN_RESPONSE_KEY).get();
        LoginResponseMessage msg = new LoginResponseMessage(response);

        // Write initial message.
        ByteBuf initialMsg = ByteMessage.pooledBuffer(Long.BYTES);
        try {
            initialMsg.writeLong(0);
        } finally {
            channel.write(initialMsg, channel.voidPromise());
        }

        // Write response message.
        return channel.writeAndFlush(msg);
    }

    /**
     * Retrieves the IP address.
     *
     * @param remoteAddress The socket address.
     * @return The IP address.
     */
    private String ipAddress(InetSocketAddress remoteAddress) {
        InetAddress inet = remoteAddress.getAddress();
        return inet.getHostAddress();
    }

    /**
     * Sets the {@code RESPONSE_KEY} attribute to the argued response.
     *
     * @param ctx The context containing the channel.
     * @param response The login response to set.
     */
    private void response(ChannelHandlerContext ctx, LoginResponse response) {
        Channel channel = ctx.channel();
        channel.attr(LOGIN_RESPONSE_KEY).set(response);
    }

    public void addToBlacklist(String address) {
        blacklist.add(address);
    }
}
