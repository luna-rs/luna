package io.luna.net;

import static io.luna.net.LunaNetworkConstants.IGNORED_EXCEPTIONS;
import io.luna.net.msg.Message;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;

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

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable e) {
        Optional<String> msg = Optional.ofNullable(e.getMessage());

        msg.filter(IGNORED_EXCEPTIONS::contains).ifPresent(it -> LOGGER.catching(e));
        ctx.channel().close();
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent event = (IdleStateEvent) evt;

            if (event.state() == IdleState.READER_IDLE) {
                ctx.channel().close();
            }
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {

        // TODO: Queue the player for logout, clean up any resources if any,
        // etc.
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Message msg) throws Exception {

        // TODO: Abstraction session model for upstream Netty message
        // management, IdleSession, LoginSession, GameSession, etc.
    }
}
