package io.luna.net.client;

import com.google.common.base.MoreObjects;
import io.luna.game.model.mob.bot.io.BotChannel;
import io.luna.net.LunaChannelFilter;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.util.AttributeKey;
import io.netty.util.NetUtil;
import io.netty.util.internal.TypeParameterMatcher;

import java.net.InetSocketAddress;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Objects;

/**
 * A model representing a connection performing network I/O at different stages. It acts sort of like
 * a wrapper for {@link Channel} that provides more specific functions for the Runescape protocol. Subclasses
 * can override the various event listeners to control client functions.
 *
 * @param <I> The type of event that this client is listening for.
 * @author lare96
 */
public abstract class Client<I> {

    /**
     * An attribute key used to retrieve the client instance.
     */
    public static final AttributeKey<Client<?>> KEY = AttributeKey.valueOf("Client.key");

    /**
     * Canonical numeric identity resolved by an admitted transport before constructing its clients.
     */
    private static final AttributeKey<String> VERIFIED_IP = AttributeKey.valueOf("Client.verifiedIp");

    /**
     * Records an immutable numeric peer address after transport-level proxy verification.
     * <p>
     * This method only normalizes the address; the caller must have authenticated its proxy source.
     * It does not perform DNS resolution and cannot replace an identity already attached to the channel.
     *
     * @param channel The connection.
     * @param address The verified IPv4 or IPv6 address, without a port.
     * @throws IllegalArgumentException If the address is not numeric.
     * @throws IllegalStateException If a different identity was previously attached.
     */
    public static void setIpAddress(Channel channel, String address) {
        byte[] bytes = address == null ? null : NetUtil.createByteArrayFromIpAddressString(address);
        if (bytes == null) {
            throw new IllegalArgumentException("A verified client address must be an IP literal.");
        }
        try {
            String canonical = InetAddress.getByAddress(bytes).getHostAddress();
            String existing = channel.attr(VERIFIED_IP).setIfAbsent(canonical);
            if (existing != null && !existing.equals(canonical)) {
                throw new IllegalStateException("A connection's verified client address cannot change.");
            }
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Invalid client IP address.", e);
        }
    }

    /**
     * Retrieves the IP address for {@code channel}.
     *
     * @param channel The channel.
     * @return The IP address of the channel.
     */
    public static String getIpAddress(Channel channel) {
        if(channel == BotChannel.CHANNEL) {
            return "bot-client";
        }
        String verified = channel.hasAttr(VERIFIED_IP) ? channel.attr(VERIFIED_IP).get() : null;
        if (verified != null) {
            return verified;
        }
        try {
            InetSocketAddress socketAddress = (InetSocketAddress) channel.remoteAddress();
            return socketAddress.getAddress().getHostAddress();
        } catch (NullPointerException e) {
            return "localhost";
        }
    }

    /**
     * The underlying channel.
     */
    final Channel channel;

    /**
     * The IP address.
     */
    final String ipAddress;

    /**
     * The message type matcher.
     */
    private final TypeParameterMatcher parameterMatcher;

    /**
     * Creates a new {@link Client}.
     *
     * @param channel The underlying channel.
     */
    Client(Channel channel) {
        this.channel = channel;
        ipAddress = getIpAddress(channel);
        parameterMatcher = TypeParameterMatcher.find(this, Client.class, "I");
    }

    @Override
    public final boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof Client<?>) {
            Client<?> other = (Client<?>) obj;
            return channel.equals(other.channel);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(channel);
    }

    @Override
    public final String toString() {
        return MoreObjects.toStringHelper(this).add("ip", ipAddress).add("channel", channel).toString();
    }

    /**
     * A fundamental part of Client implementations. Handles incoming message {@code I} from Netty.
     *
     * @param msg The message to handle.
     * @throws Exception If any errors occur.
     */
    abstract void onMessageReceived(I msg) throws Exception;

    /**
     * Called when the underlying channel is disconnected.
     */
    public void onInactive() {

    }

    /**
     * Called when the underlying channel throws an exception.
     *
     * @param e The exception thrown.
     */
    public void onException(Throwable e) {

    }

    /**
     * Forwards a call to {@link #onMessageReceived(I)} only if the type of Object {@code msg}
     * matches {@link I}.
     *
     * @param msg The message to forward.
     * @throws Exception If any errors occur.
     */
    @SuppressWarnings("unchecked")
    public final void messageReceived(Object msg) throws Exception {
        // parameterMatcher makes the following cast type-safe.
        if (parameterMatcher.match(msg)) {
            //noinspection unchecked
            onMessageReceived((I) msg);
        }
    }

    /**
     * Closes this channel.
     */
    public final ChannelFuture disconnect() {
        return channel.close();
    }

    /**
     * @return The client's channel.
     */
    public final Channel getChannel() {
        return channel;
    }

    /**
     * @return The client's channel filter.
     */
    public final LunaChannelFilter getChannelFilter() {
        return channel.attr(LunaChannelFilter.KEY).get();
    }

    /**
     * @return The client's IP address.
     */
    public final String getIpAddress() {
        return ipAddress;
    }
}
