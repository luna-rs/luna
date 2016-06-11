package io.luna.net;

import io.luna.net.session.Session;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.ReadTimeoutException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Optional;

import static java.util.Objects.requireNonNull;

/**
 * A {@link SimpleChannelInboundHandler} implementation that handles upstream messages from Netty.
 *
 * @author lare96 <http://github.com/lare96>
 */
@Sharable public final class LunaUpstreamHandler extends SimpleChannelInboundHandler<Object> {

    /**
     * The asynchronous logger.
     */
    private static final Logger LOGGER = LogManager.getLogger();

    /**
     * A default access level constructor to discourage external instantiation outside of the {@code io.luna.net} package.
     */
    LunaUpstreamHandler() {
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable e) {
        boolean channelReadTimeout = e instanceof ReadTimeoutException;

        if (!channelReadTimeout) {
            Optional<String> msg = Optional.ofNullable(e.getMessage());
            msg.filter(it -> !LunaNetworkConstants.IGNORED_EXCEPTIONS.contains(it)).ifPresent(it -> LOGGER.catching(e));
        }
        ctx.channel().close();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        Session session = getSession(ctx);
        session.dispose();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
        Session session = getSession(ctx);
        session.handleUpstreamMessage(msg);
    }

    /**
     * Gets the {@link Session} instance from the {@link ChannelHandlerContext}, and validates it to ensure it isn't {@code
     * null}.
     *
     * @param ctx The channel handler context.
     * @return The session instance.
     */
    private Session getSession(ChannelHandlerContext ctx) {
        Session session = ctx.channel().attr(LunaNetworkConstants.SESSION_KEY).get();
        return requireNonNull(session, "session == null");
    }
}
