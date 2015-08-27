package io.luna.net.codec.game;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.util.List;

/**
 * The {@link io.netty.handler.codec.ByteToMessageDecoder} implementation that
 * decodes all upstream packets.
 * 
 * @author lare96 <http://github.org/lare96>
 */
public final class MessageDecoder extends ByteToMessageDecoder {

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {

    }
}
