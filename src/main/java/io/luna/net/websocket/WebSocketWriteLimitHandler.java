package io.luna.net.websocket;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.util.ReferenceCountUtil;

import java.io.IOException;

/**
 * Bounds queued wire output, including HTTP handshakes and WebSocket control replies.
 *
 * @author lare96
 */
final class WebSocketWriteLimitHandler extends ChannelOutboundHandlerAdapter {

    private final long limit;
    private long pendingBytes;

    WebSocketWriteLimitHandler(long limit) {
        this.limit = limit;
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        int length = msg instanceof ByteBuf bytes ? bytes.readableBytes()
                : msg instanceof ByteBufHolder holder ? holder.content().readableBytes() : 0;
        if (length > limit - pendingBytes) {
            ReferenceCountUtil.release(msg);
            promise.tryFailure(new IOException("The browser connection exceeded its queued wire byte limit."));
            ctx.close();
            return;
        }
        pendingBytes += length;
        ChannelPromise tracked = promise.isVoid() ? ctx.newPromise() : promise;
        tracked.addListener(ignored -> pendingBytes -= length);
        ctx.write(msg, tracked);
    }
}
