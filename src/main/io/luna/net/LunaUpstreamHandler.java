package io.luna.net;

import io.luna.net.session.Session;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.ReadTimeoutException;

import java.util.Objects;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A {@link io.netty.channel.SimpleChannelInboundHandler} implementation that
 * handles upstream messages from Netty.
 *
 * @author lare96 <http://github.com/lare96>
 */
@Sharable
public final class LunaUpstreamHandler extends SimpleChannelInboundHandler<Object> {

    /**
     * The logger that will print important information.
     */
    private static final Logger LOGGER = LogManager.getLogger(LunaUpstreamHandler.class);

    /**
     * A default access level constructor to discourage external instantiation
     * outside of the {@code io.luna.net} package.
     */
    LunaUpstreamHandler() {}

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable e) {
        boolean channelReadTimeout = e instanceof ReadTimeoutException;

        if (!channelReadTimeout) {
            Optional<String> msg = Optional.ofNullable(e.getMessage());
            msg.filter(LunaNetworkConstants.IGNORED_EXCEPTIONS::contains).ifPresent(it -> LOGGER.catching(e));
        }
        ctx.channel().close();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        // Session session = getSession(ctx);
        // TODO: Queue the player for logout, clean up any resources if any,
        // etc.
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
        Session session = getSession(ctx);
        session.handleUpstreamMessage(msg);
    }

    /**
     * Gets the {@link io.luna.net.session.Session} instance from the
     * {@link io.netty.channel.ChannelHandlerContext}, and validates it to
     * ensure it isn't {@code null}.
     * 
     * @param ctx
     *            The channel handler context.
     * @return The session instance.
     */
    private Session getSession(ChannelHandlerContext ctx) {
        Session session = ctx.channel().attr(LunaNetworkConstants.SESSION_KEY).get();
        return Objects.requireNonNull(session, "session == null");
    }
}
