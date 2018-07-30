package io.luna.net.session;

import io.netty.channel.Channel;

import java.net.InetSocketAddress;

import static com.google.common.base.Preconditions.checkState;

/**
 * A model representing a client connected to this server.
 *
 * @author lare96 <http://github.org/lare96>
 */
public class Session {

    /**
     * The client's channel.
     */
    private final Channel channel;

    /**
     * The client's IP address.
     */
    private final String hostAddress;

    /**
     * Creates a new {@link Session}.
     *
     * @param channel The client's channel.
     */
    public Session(Channel channel) {
        this.channel = channel;
        this.hostAddress = ((InetSocketAddress) channel.remoteAddress()).getAddress().getHostAddress();
    }

    /**
     * Executed when {@code close()} is invoked.
     */
    public void onDispose() {

    }

    /**
     * Implementations decide how messages are handled. Messages are ignored completely by default.
     */
    public void handleUpstreamMessage(Object msg) throws Exception {
    }

    /**
     * @return The client's channel.
     */
    public final Channel getChannel() {
        return channel;
    }

    /**
     * @return The client's IP address.
     */
    public final String getHostAddress() {
        return hostAddress;
    }
}
