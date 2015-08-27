package io.luna.net.codec.game;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

/**
 * The {@link io.netty.handler.codec.MessageToByteEncoder} implementation that
 * encodes all downstream packets.
 * 
 * @author lare96 <http://github.org/lare96>
 */
@Sharable
public final class MessageEncoder extends MessageToByteEncoder<Object> {

    @Override
    protected void encode(ChannelHandlerContext ctx, Object msg, ByteBuf out) throws Exception {

    }
}
