package io.luna.net.client;

import io.netty.channel.Channel;

/**
 * A {@link Client} implementation model representing pre-login I/O communications. Throws an exception if
 * any messages are received as the client is not yet ready to handle them.
 *
 * @author lare96 <http://github.com/lare96>
 */
public class IdleClient extends Client<Object> {

    /**
     * Creates a new {@link IdleClient}.
     *
     * @param channel The client's channel.
     */
    public IdleClient(Channel channel) {
        super(channel);
    }

    @Override
    void onMessageReceived(Object msg) {
        throw new UnsupportedOperationException("Not ready for I/O.");
    }
}