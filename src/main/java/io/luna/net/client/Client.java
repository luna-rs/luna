package io.luna.net.client;

import com.google.common.base.MoreObjects;
import io.luna.game.model.mob.bot.BotChannel;
import io.luna.net.LunaChannelFilter;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.util.AttributeKey;
import io.netty.util.internal.TypeParameterMatcher;

import java.net.InetSocketAddress;
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
     * Retrieves the IP address for {@code channel}.
     *
     * @param channel The channel.
     * @return The IP address of the channel.
     */
    public static String getIpAddress(Channel channel) {
        if(channel == BotChannel.CHANNEL) {
            return "bot-client";
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
