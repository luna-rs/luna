package io.luna.net;

import io.luna.LunaContext;
import io.luna.net.codec.login.LoginDecoder;
import io.luna.net.codec.login.LoginEncoder;
import io.luna.net.msg.MessageRepository;
import io.luna.net.session.Client;
import io.luna.net.session.IdleClient;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.timeout.ReadTimeoutHandler;

/**
 * A {@link ChannelInitializer} implementation that will initialize {@link SocketChannel}s before they are
 * registered.
 *
 * @author lare96 <http://github.com/lare96>
 */
@Sharable
public final class LunaChannelInitializer extends ChannelInitializer<SocketChannel> {

    /**
     * Handles upstream messages from Netty.
     */
    private final ChannelHandler upstreamHandler = new LunaUpstreamHandler();

    /**
     * Encodes the login response.
     */
    private final ChannelHandler loginEncoder = new LoginEncoder();

    /**
     * The read timeout handler.
     */
    private final ChannelHandler readTimeout = new ReadTimeoutHandler(5);

    /**
     * A channel handler that will filter channels.
     */
    private final LunaChannelFilter channelFilter;

    /**
     * Decodes the login protocol.
     */
    private final ChannelHandler loginDecoder;

    /**
     * Creates a new {@link LunaChannelInitializer}.
     *
     * @param context The context instance.
     * @param channelFilter A channel handler that will filter channels.
     * @param msgRepository The message repository.
     */
    public LunaChannelInitializer(LunaContext context, LunaChannelFilter channelFilter,
                                  MessageRepository msgRepository) {
        this.channelFilter = channelFilter;
        loginDecoder = new LoginDecoder(context, msgRepository);
    }

    @Override
    protected void initChannel(SocketChannel ch) throws Exception {
        ch.attr(Client.KEY).setIfAbsent(new IdleClient(ch));

        ch.pipeline().addLast("read-timeout", readTimeout);
        ch.pipeline().addLast("channel-filter", channelFilter);
        ch.pipeline().addLast("login-decoder", loginDecoder);
        ch.pipeline().addLast("login-encoder", loginEncoder);
        ch.pipeline().addLast("upstream-handler", upstreamHandler);
    }
}
