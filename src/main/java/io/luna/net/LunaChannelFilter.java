package io.luna.net;

import com.google.common.collect.ConcurrentHashMultiset;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multiset;
import com.google.common.collect.Sets;
import io.luna.LunaConstants;
import io.luna.net.codec.login.LoginResponse;
import io.luna.net.codec.login.LoginResponseMessage;
import io.luna.util.parser.NewLineParser;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.ipfilter.AbstractRemoteAddressFilter;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Set;

/**
 * An {@link AbstractRemoteAddressFilter} implementation that filters {@link Channel}s by the amount of active connections
 * they already have and whether or not they are blacklisted. A threshold is put on the amount of successful connections
 * allowed to be made in order to provide security from socket flooder attacks.
 * <p>
 * <p>
 * <strong>One {@code LunaChannelFilter} instance must be shared across all pipelines in order to ensure that every channel
 * is using the same multiset.</strong>
 *
 * @author lare96 <http://github.org/lare96>
 */
@Sharable public final class LunaChannelFilter extends AbstractRemoteAddressFilter<InetSocketAddress> {

    /**
     * A {@link NewLineParser} implementation that parses blacklisted addresses.
     *
     * @author lare96 <http://github.org/lare96>
     */
    private final class BlacklistParser extends NewLineParser {

        /**
         * Creates a new {@link BlacklistParser}.
         */
        public BlacklistParser() {
            super("./data/punishment/blacklist.txt");
        }

        @Override
        public void readNextLine(String nextLine) throws Exception {
            blacklist.add(nextLine);
        }
    }

    /**
     * An {@link ImmutableSet} containing whitelisted addresses. Addresses within this {@code ImmutableSet} bypass channel
     * filtering completely.
     */
    public static final ImmutableSet<String> WHITELIST = ImmutableSet.of("127.0.0.1");

    /**
     * An {@link AttributeKey} used to access an {@link Attribute} describing which {@link LoginResponse} should be sent for
     * rejected channels.
     */
    private static final AttributeKey<LoginResponse> RESPONSE_KEY = AttributeKey.valueOf("channel.RESPONSE_KEY");

    /**
     * A concurrent {@link Multiset} containing active connections.
     */
    private final Multiset<String> connections = ConcurrentHashMultiset.create();

    /**
     * A concurrent {@link Set} containing blacklisted addresses.
     */
    private final Set<String> blacklist = Sets.newConcurrentHashSet();

    {
        NewLineParser parser = new BlacklistParser();
        parser.run();
    }

    @Override
    protected boolean accept(ChannelHandlerContext ctx, InetSocketAddress remoteAddress) throws Exception {
        String address = address(remoteAddress);

        if (WHITELIST.contains(address)) { // Bypass filter for whitelisted addresses.
            return true;
        }

        int limit = LunaConstants.CONNECTION_LIMIT;
        if (connections.count(address) >= limit) { // Reject if more than CONNECTION_LIMIT active connections.
            response(ctx, LoginResponse.LOGIN_LIMIT_EXCEEDED);
            return false;
        }
        if (blacklist.contains(address)) { // Reject if blacklisted.
            response(ctx, LoginResponse.ACCOUNT_BANNED);
            return false;
        }
        return true;
    }

    @Override
    protected void channelAccepted(ChannelHandlerContext ctx, InetSocketAddress remoteAddress) {
        String address = address(remoteAddress);

        ChannelFuture future = ctx.channel().closeFuture(); // Remove address once disconnected.
        future.addListener(it -> connections.remove(address));

        connections.add(address);
    }

    @Override
    protected ChannelFuture channelRejected(ChannelHandlerContext ctx, InetSocketAddress remoteAddress) {
        Channel channel = ctx.channel();

        LoginResponse response = channel.attr(RESPONSE_KEY).get(); // Retrieve the response message.
        LoginResponseMessage message = new LoginResponseMessage(response);

        ByteBuf initialMessage = ctx.alloc().buffer(8).writeLong(0); // Write initial message.
        channel.write(initialMessage, channel.voidPromise());
        return channel.writeAndFlush(message); // Write response message.
    }

    /**
     * Retrieves the host address name from the {@link InetSocketAddress}.
     */
    private String address(InetSocketAddress remoteAddress) {
        InetAddress inet = remoteAddress.getAddress();
        return inet.getHostAddress();
    }

    /**
     * Sets the {@code RESPONSE_KEY} attribute to {@code response}.
     */
    private void response(ChannelHandlerContext ctx, LoginResponse response) {
        Channel channel = ctx.channel();
        channel.attr(RESPONSE_KEY).set(response);
    }
}
