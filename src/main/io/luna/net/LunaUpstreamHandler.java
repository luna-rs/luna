package io.luna.net;

import static com.google.common.base.Preconditions.checkState;
import io.luna.net.msg.Message;
import io.luna.net.session.Session;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

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
public final class LunaUpstreamHandler extends SimpleChannelInboundHandler<Message> {

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
        Optional<String> msg = Optional.ofNullable(e.getMessage());
        msg.filter(LunaNetworkConstants.IGNORED_EXCEPTIONS::contains).ifPresent(it -> LOGGER.catching(e));

        ctx.channel().close();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {

        // TODO: Queue the player for logout, clean up any resources if any,
        // etc.
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Message msg) throws Exception {
        Session session = ctx.channel().attr(LunaNetworkConstants.SESSION_KEY).get();
        checkState(session != null, "session == null");
        session.handleUpstreamMessage(msg);
    }
}
