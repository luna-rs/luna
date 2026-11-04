package io.luna.net;

import io.luna.LunaContext;
import io.luna.net.client.Client;
import io.luna.net.client.IdleClient;
import io.luna.net.msg.login.LoginDecoder;
import io.luna.net.msg.login.LoginEncoder;
import io.luna.net.msg.GameMessageRepository;
import io.netty.channel.ChannelHandler;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.timeout.ReadTimeoutHandler;

/**
 * A {@link ChannelInitializer} implementation that will initialize {@link SocketChannel}s before they are
 * registered.
 *
 * @author lare96 
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
     * The context instance.
     */
    private final LunaContext context;

    /**
     * A channel handler that will filter channels.
     */
    private final LunaChannelFilter channelFilter;

    /**
     * The message repository.
     */
    private final GameMessageRepository msgRepository;

    /**
     * Creates a new {@link LunaChannelInitializer}.
     *
     * @param context The context instance.
     * @param channelFilter A channel handler that will filter channels.
     * @param msgRepository The message repository.
     */
    public LunaChannelInitializer(LunaContext context, LunaChannelFilter channelFilter,
                                  GameMessageRepository msgRepository) {
        this.context = context;
        this.channelFilter = channelFilter;
        this.msgRepository = msgRepository;
    }

    @Override
    protected void initChannel(SocketChannel ch) throws Exception {
        addGameHandlers(ch);
        ch.pipeline().addBefore("login-decoder", "channel-filter", channelFilter);
    }

    /**
     * Installs the client state, read timeout and login/game protocol shared by both transports.
     * <p>
     * TCP initialization inserts the admission filter before its login decoder. The browser transport
     * calls this method after a successful WebSocket upgrade and admission. Handler names must remain
     * stable because {@link io.luna.net.client.LoginClient} replaces the login codecs after authentication.
     *
     * @param ch The channel whose underlying transport carries ordered game bytes.
     */
    public void addGameHandlers(Channel ch) {
        ch.attr(Client.KEY).setIfAbsent(new IdleClient(ch));
        ch.attr(LunaChannelFilter.KEY).setIfAbsent(channelFilter);

        ch.pipeline().addLast("read-timeout", new ReadTimeoutHandler(5));
        ch.pipeline().addLast("login-decoder", new LoginDecoder(context, msgRepository));
        ch.pipeline().addLast("login-encoder", loginEncoder);
        ch.pipeline().addLast("upstream-handler", upstreamHandler);
    }
}
