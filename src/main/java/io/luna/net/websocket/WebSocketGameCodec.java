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
 * <p>
 * A WebSocket message is a transport chunk, not a game packet: the shared decoders must still accumulate partial
 * packets and consume multiple packets from one chunk. A frame aggregator and Netty's protocol handler precede this
 * handler, so complete binary messages arrive here after fragmentation and control-frame processing.
 * <p>
 * This handler is created per connection. Its queue accounting and write-completion listeners run on the channel's
 * event loop. It bounds game payload awaiting write completion; {@link WebSocketWriteLimitHandler} also bounds encoded
 * wire output, including HTTP and control replies that originate earlier in the pipeline.
 *
 * @author lare96
 */
final class WebSocketGameCodec extends ChannelDuplexHandler {

    /**
     * Maximum number of game payload bytes awaiting successful or failed write completion.
     */
    private final long maxPendingOutboundBytes;

    /**
     * Payload bytes still owned by pending outbound writes, including writes not yet flushed.
     */
    private long pendingOutboundBytes;

    /**
     * Prevents additional game input or duplicate close frames after a transport rejection.
     */
    private boolean closing;

    /**
     * Creates the binary game transport for one browser connection.
     *
     * @param maxPendingOutboundBytes The positive per-connection game payload limit in bytes.
     */
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
                    // Transfer one retained content reference downstream before releasing its owning frame.
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
        // Void promises cannot notify accounting when Netty releases the pending write.
        ChannelPromise tracked = promise.isVoid() ? ctx.newPromise() : promise;
        tracked.addListener(ignored -> pendingOutboundBytes -= length);
        // The frame takes ownership of the existing buffer; no copy or extra retain is required.
        ctx.write(new BinaryWebSocketFrame(bytes), tracked);
    }

    /**
     * Sends at most one protocol close frame and closes the connection when that write completes.
     * The preceding WebSocket protocol handler also supplies the configured forced-close deadline.
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
