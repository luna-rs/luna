package io.luna.net;

import io.luna.net.session.Session;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.ReadTimeoutException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Optional;
import java.util.function.Supplier;

import static com.google.common.base.Predicates.in;
import static com.google.common.base.Predicates.instanceOf;
import static com.google.common.base.Predicates.not;
import static java.util.Objects.requireNonNull;

/**
 * A {@link SimpleChannelInboundHandler} implementation that handles upstream messages from Netty.
 *
 * @author lare96 <http://github.com/lare96>
 */
@Sharable
public final class LunaUpstreamHandler extends SimpleChannelInboundHandler<Object> {

    /**
     * The asynchronous logger.
     */
    private static final Logger LOGGER = LogManager.getLogger();

    /**
     * A default access level constructor to discourage external instantiation.
     */
    LunaUpstreamHandler() {
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable e) {
        Supplier<String> warnMsg = () ->
                "Disconnecting " + getSession(ctx) + ", upstream exception thrown.";

        Optional.of(e).filter(not(instanceOf(ReadTimeoutException.class))).
                map(Throwable::getMessage).
                filter(not(in(LunaNetworkConstants.IGNORED_EXCEPTIONS))).
                ifPresent(it -> LOGGER.warn(warnMsg, e));

        ctx.channel().close();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        Session session = getSession(ctx);
        session.onDispose();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
        Session session = getSession(ctx);
        session.handleUpstreamMessage(msg);
    }

    /**
     * Retrieves and validates a session instance from a {@link ChannelHandlerContext}.
     */
    private Session getSession(ChannelHandlerContext ctx) {
        Session session = ctx.channel().attr(LunaNetworkConstants.SESSION_KEY).get();
        return requireNonNull(session, "session == null");
    }
}
