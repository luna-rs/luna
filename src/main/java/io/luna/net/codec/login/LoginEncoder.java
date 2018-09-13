package io.luna.net.codec.login;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

/**
 * A {@link MessageToByteEncoder} implementation that encodes the login response.
 *
 * @author lare96 <http://github.org/lare96>
 */
@Sharable public final class LoginEncoder extends MessageToByteEncoder<LoginResponseMessage> {

    @Override
    protected void encode(ChannelHandlerContext ctx, LoginResponseMessage msg, ByteBuf out) throws Exception {
        out.writeByte(msg.getResponse().getOpcode());

        if (msg.getResponse() == LoginResponse.NORMAL) {
            out.writeByte(msg.getRights().getClientValue());
            out.writeBoolean(msg.isSuspectedBot());
        }
    }
}
