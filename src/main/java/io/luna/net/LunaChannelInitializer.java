package io.luna.net;

import io.luna.LunaContext;
import io.luna.net.client.Client;
import io.luna.net.client.IdleClient;
import io.luna.net.msg.login.LoginDecoder;
import io.luna.net.msg.login.LoginEncoder;
import io.luna.net.codec.login.LoginDecoder;
import io.luna.net.codec.login.LoginEncoder;
<<<<<<< HEAD
=======
import io.luna.security.RsaKey;
>>>>>>> ad60462186dcfbcc57fed662d7eab8f8bb67c550
import io.luna.security.RsaKeyReader;
import io.luna.security.TomlPrivateRsaKeyReader;
import io.luna.net.msg.GameMessageRepository;
import io.netty.channel.ChannelHandler;
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
     * The private RSA key, only to be seen by the server.
     */
    private final RsaKey privateKey;

    /**
     * Creates a new {@link LunaChannelInitializer}.
     *
     * @param context       The context instance.
     * @param channelFilter A channel handler that will filter channels.
     * @param msgRepository The message repository.
     */
    public LunaChannelInitializer(LunaContext context, LunaChannelFilter channelFilter,
                                  GameMessageRepository msgRepository) {
        this.context = context;
        this.channelFilter = channelFilter;
        this.msgRepository = msgRepository;

        RsaKeyReader reader = new TomlPrivateRsaKeyReader();
        this.privateKey = reader.read();
    }

    @Override
    protected void initChannel(SocketChannel ch) throws Exception {
        ch.attr(Client.KEY).setIfAbsent(new IdleClient(ch));
        ch.attr(LunaChannelFilter.KEY).setIfAbsent(channelFilter);

        ch.pipeline().addLast("read-timeout", new ReadTimeoutHandler(5));
        ch.pipeline().addLast("channel-filter", channelFilter);
<<<<<<< HEAD

        RsaKeyPairReader reader = new TomlRsaPrivateKeyPairReader();
        RsaKeyPair privateKeyPair = reader.read();

        ch.pipeline().addLast("login-decoder", new LoginDecoder(context, msgRepository, privateKeyPair));
=======
        ch.pipeline().addLast("login-decoder", new LoginDecoder(context, msgRepository, privateKey));
>>>>>>> ad60462186dcfbcc57fed662d7eab8f8bb67c550
        ch.pipeline().addLast("login-encoder", loginEncoder);
        ch.pipeline().addLast("upstream-handler", upstreamHandler);
    }
}
