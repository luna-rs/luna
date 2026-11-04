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
 * <p>
 * This per-connection handler precedes the transport encoders. Outbound writes travel toward the pipeline head, so it
 * sees their encoded buffers, including replies produced by handlers that precede the game codec. Accounting is
 * confined to the channel event loop and includes unflushed writes until their promises succeed or fail.
 * <p>
 * Exceeding the limit releases the rejected message and immediately closes the socket. Sending another close frame
 * would add data to the already-full output queue.
 *
 * @author lare96
 */
final class WebSocketWriteLimitHandler extends ChannelOutboundHandlerAdapter {

    /**
     * Maximum queued wire bytes allowed for this connection.
     */
    private final long limit;

    /**
     * Bytes owned by outbound writes whose promises have not completed.
     */
    private long pendingBytes;

    /**
     * Creates a wire-output guard for one connection.
     *
     * @param limit The byte limit from the validated listener settings.
     */
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
        // Replace void promises so both successful writes and connection failures release their accounting.
        ChannelPromise tracked = promise.isVoid() ? ctx.newPromise() : promise;
        tracked.addListener(ignored -> pendingBytes -= length);
        ctx.write(msg, tracked);
    }
}
