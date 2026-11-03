package io.luna.net.websocket;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.util.ReferenceCountUtil;

import java.io.IOException;

/**
 * Carries the existing ordered RuneScape byte stream in bounded binary WebSocket frames.
 *
 * @author lare96
 */
final class WebSocketGameCodec extends ChannelDuplexHandler {

    private final long maxPendingOutboundBytes;
    private long pendingOutboundBytes;
    private boolean closing;

    WebSocketGameCodec(long maxPendingOutboundBytes) {
        if (maxPendingOutboundBytes < 1) {
            throw new IllegalArgumentException("The outbound byte limit must be positive.");
        }
        this.maxPendingOutboundBytes = maxPendingOutboundBytes;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof BinaryWebSocketFrame frame) {
            try {
                if (!closing) {
                    ctx.fireChannelRead(frame.content().retain());
                }
            } finally {
                frame.release();
            }
        } else if (msg instanceof WebSocketFrame) {
            ReferenceCountUtil.release(msg);
            close(ctx, 1003, "Binary game packets required");
        } else {
            ctx.fireChannelRead(msg);
        }
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (!(msg instanceof ByteBuf bytes)) {
            ctx.write(msg, promise);
            return;
        }
        int length = bytes.readableBytes();
        if (closing || length > maxPendingOutboundBytes - pendingOutboundBytes) {
            bytes.release();
            promise.tryFailure(new IOException("The browser connection exceeded its outbound byte limit."));
            close(ctx, 1008, "Game output queue limit exceeded");
            return;
        }
        pendingOutboundBytes += length;
        ChannelPromise tracked = promise.isVoid() ? ctx.newPromise() : promise;
        tracked.addListener(ignored -> pendingOutboundBytes -= length);
        ctx.write(new BinaryWebSocketFrame(bytes), tracked);
    }

    /**
     * Sends at most one protocol close frame and releases the connection.
     *
     * @param ctx The transport context.
     * @param code The WebSocket status code.
     * @param reason The public reason.
     */
    private void close(ChannelHandlerContext ctx, int code, String reason) {
        if (!closing) {
            closing = true;
            ctx.writeAndFlush(new CloseWebSocketFrame(code, reason)).addListener(ChannelFutureListener.CLOSE);
        }
    }
}
