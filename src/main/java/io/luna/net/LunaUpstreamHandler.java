package io.luna.net;

import io.luna.net.client.Client;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.ReadTimeoutException;
import io.netty.util.ReferenceCountUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Set;

import static java.util.Objects.requireNonNull;

/**
 * A {@link ChannelInboundHandlerAdapter} implementation that handles upstream messages from Netty.
 * Only one instance of this class should ever exist.
 *
 * @author lare96 
 */
@Sharable
public final class LunaUpstreamHandler extends ChannelInboundHandlerAdapter {

    /**
     * A set of ignored exceptions from Netty.
     */
    private static final Set<String> IGNORED = Set.of(
        "An existing connection was forcibly closed by the remote host",
        "An established connection was aborted by the software in your host machine"
    );

    /**
     * The asynchronous logger.
     */
    private static final Logger logger = LogManager.getLogger();

    /**
     * A default access level constructor.
     */
    public LunaUpstreamHandler() {
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable e) {
        Client<?> client = getClient(ctx);
        boolean isReadTimeout = e instanceof ReadTimeoutException;
        boolean isIgnoredMessage = IGNORED.contains(e.getMessage());

        if (!isReadTimeout && !isIgnoredMessage) {
            logger.warn("Disconnecting " + client + ", upstream exception thrown.", e);
        }
        client.onException(e);
        ctx.channel().close();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        Client<?> client = getClient(ctx);
        client.onInactive();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        try {
            Client<?> client = getClient(ctx);
            client.messageReceived(msg);
        } finally {
            ReferenceCountUtil.release(msg);
        }
    }

    /**
     * Retrieves the client instance from the {@link ChannelHandlerContext}.
     *
     * @param ctx The context containing the channel.
     * @return The client.
     */
    private Client<?> getClient(ChannelHandlerContext ctx) {
        Client<?> client = ctx.channel().attr(Client.KEY).get();
        return requireNonNull(client);
    }
}
