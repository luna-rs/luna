package io.luna.net.session;

import io.luna.game.model.mobile.Player;
import io.netty.channel.Channel;

import java.net.InetSocketAddress;

import static com.google.common.base.Preconditions.checkState;

/**
 * An abstraction model that determines how I/O operations are handled for a
 * {@link Player}.
 * 
 * @author lare96 <http://github.org/lare96>
 */
public class Session {

    /**
     * The {@link Channel} to send and receive messages through.
     */
    private final Channel channel;

    /**
     * The address that the connection was received from.
     */
    private final String hostAddress;

    /**
     * The current state of this {@code Session}.
     */
    private SessionState state = SessionState.CONNECTED;

    /**
     * Creates a new {@link Session}.
     *
     * @param channel The {@link Channel} to send and receive messages through.
     */
    public Session(Channel channel) {
        this.channel = channel;
        this.hostAddress = ((InetSocketAddress) channel.remoteAddress()).getAddress().getHostAddress();
    }

    /**
     * Disposes of this {@code Session} by closing the {@link Channel} and executing the {@code onDispose()} listener.
     */
    public final void dispose() {
        Channel channel = getChannel();
        checkState(!channel.isActive(), "call getChannel().close() instead!");

        onDispose();
    }

    /**
     * Executed when this {@link Session} needs to be disposed of.
     */
    public void onDispose() {

    }

    /**
     * Implementations decide which messages are handled and how they are
     * handled. Messages are ignored completely by default.
     * 
     * @param msg The message to handle.
     */
    public void handleUpstreamMessage(Object msg) throws Exception {}

    /**
     * @return The {@link Channel} to send and receive messages through.
     */
    public final Channel getChannel() {
        return channel;
    }

    /**
     * @return The address that the connection was received from.
     */
    public final String getHostAddress() {
        return hostAddress;
    }

    /**
     * @return The current state of this {@code Session}.
     */
    public final SessionState getState() {
        return state;
    }

    /**
     * Sets the value for {@link Session#state}.
     */
    public final void setState(SessionState state) {
        this.state = state;
    }
}
