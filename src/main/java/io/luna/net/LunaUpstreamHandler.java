package io.luna.net;

import com.google.common.collect.ImmutableSet;
import io.luna.net.session.Client;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.ReadTimeoutException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Set;

import static java.util.Objects.requireNonNull;

/**
 * A {@link SimpleChannelInboundHandler} implementation that handles upstream messages from Netty.
 * Only one instance of this class should ever exist.
 *
 * @author lare96 <http://github.com/lare96>
 */
@Sharable
public final class LunaUpstreamHandler extends SimpleChannelInboundHandler<Object> {

    /**
     * A set of ignored exceptions from Netty.
     */
    private static final Set<String> IGNORED = ImmutableSet
            .of("An existing connection was forcibly closed by the remote host",
                    "An established connection was aborted by the software in your host machine");

    /**
     * The asynchronous logger.
     */
    private static final Logger LOGGER = LogManager.getLogger();

    /**
     * A default access level constructor.
     */
    LunaUpstreamHandler() {
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable e) {
        boolean isReadTimeout = e instanceof ReadTimeoutException;
        boolean isIgnoredMessage = IGNORED.contains(e.getMessage());
        if(!isReadTimeout && !isIgnoredMessage) {
            LOGGER.warn("Disconnecting " + getSession(ctx) + ", upstream exception thrown.", e);
        }
        ctx.channel().close();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        Client client = getSession(ctx);
        client.onInactive();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
        Client client = getSession(ctx);
        client.onMessageReceived(msg);
    }

    /**
     * Retrieves the client instance from the {@link ChannelHandlerContext}.
     *
     * @param ctx The context containing the channel.
     * @return The client.
     */
    private Client getSession(ChannelHandlerContext ctx) {
        Client client = ctx.channel().attr(Client.KEY).get();
        return requireNonNull(client);
    }
}
