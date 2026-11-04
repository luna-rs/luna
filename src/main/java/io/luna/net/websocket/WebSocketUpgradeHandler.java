package io.luna.net.websocket;

import io.luna.net.LunaChannelFilter;
import io.luna.net.LunaChannelInitializer;
import io.luna.net.client.Client;
import io.luna.net.msg.login.LoginEncoder;
import io.luna.net.msg.login.LoginResponse;
import io.luna.net.msg.login.LoginResponseMessage;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.ScheduledFuture;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Validates browser upgrades and admits the shared game protocol only after the WebSocket handshake.
 * <p>
 * The HTTP guard sits before Netty's WebSocket protocol handler, while its {@link ReadyHandler} sits after framing and
 * the binary adapter. These two stages share state on one channel event loop. The first verifies the endpoint,
 * origin, and proxy address; the second waits for a completed upgrade, records that immutable address, applies the
 * existing admission policy, and installs the unchanged login pipeline.
 * <p>
 * An absolute deadline covers incomplete HTTP requests as well as the WebSocket handshake. No RuneScape login bytes
 * are processed before admission, and admission failures are sent only after upgrade so their existing binary login
 * response is correctly wrapped in WebSocket frames.
 *
 * @author lare96
 */
final class WebSocketUpgradeHandler extends ChannelInboundHandlerAdapter {

    /**
     * Validated endpoint, origin, proxy, and resource-limit configuration.
     */
    private final WebSocketSettings settings;

    /**
     * Shared desktop and browser connection admission policy.
     */
    private final LunaChannelFilter filter;

    /**
     * Installs the existing login codecs after successful transport admission.
     */
    private final LunaChannelInitializer gameInitializer;

    /**
     * Absolute deadline started when the HTTP guard is added, cancelled on success or teardown.
     */
    private ScheduledFuture<?> timeout;

    /**
     * Numeric client address verified from the upgrade request before its HTTP ownership is released.
     */
    private String address;

    /**
     * Allows only one validated upgrade attempt on the connection.
     */
    private boolean requestReceived;

    /**
     * Creates both admission stages for a single browser connection.
     *
     * @param settings The validated listener settings.
     * @param filter The policy shared with desktop connections.
     * @param gameInitializer The existing game protocol installer.
     */
    WebSocketUpgradeHandler(WebSocketSettings settings, LunaChannelFilter filter,
                            LunaChannelInitializer gameInitializer) {
        this.settings = settings;
        this.filter = filter;
        this.gameInitializer = gameInitializer;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        // Netty's WebSocket handshake timeout starts after HTTP decoding; this also covers incomplete HTTP headers.
        timeout = ctx.executor().schedule(() -> { ctx.close(); },
                settings.handshakeTimeoutMillis(), TimeUnit.MILLISECONDS);
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) {
        cancelTimeout();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        cancelTimeout();
        ctx.fireChannelInactive();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (!(msg instanceof FullHttpRequest request)) {
            ctx.fireChannelRead(msg);
            return;
        }
        HttpResponseStatus rejection = validate(request);
        if (rejection == null) {
            try {
                address = WebSocketPeerAddress.resolve(ctx.channel(), request, settings);
            } catch (IllegalArgumentException e) {
                rejection = HttpResponseStatus.FORBIDDEN;
            }
        }
        if (rejection != null) {
            request.release();
            reject(ctx, rejection);
            return;
        }
        requestReceived = true;
        // Netty's handshake handler takes responsibility for releasing the forwarded HTTP request.
        ctx.fireChannelRead(request);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        ctx.close();
    }

    /**
     * Creates the post-upgrade admission handler that shares this guard's verified request state.
     *
     * @return A new handler to install after WebSocket framing and the binary game adapter.
     */
    ChannelInboundHandler readyHandler() {
        return new ReadyHandler();
    }

    /**
     * Checks the fixed endpoint and browser origin before allowing an HTTP upgrade.
     * The optional {@code server} parameter preserves the existing browser client's endpoint format; it does not
     * select an upstream server because this listener belongs to the game server itself. Netty performs the remaining
     * WebSocket version, key, upgrade-header, and masking checks.
     *
     * @param request The upgrade request.
     * @return A rejection status, or {@code null} when allowed.
     */
    private HttpResponseStatus validate(FullHttpRequest request) {
        if (requestReceived || !request.decoderResult().isSuccess() || request.content().isReadable()
                || !HttpMethod.GET.equals(request.method())) {
            return HttpResponseStatus.BAD_REQUEST;
        }
        try {
            QueryStringDecoder uri = new QueryStringDecoder(request.uri());
            if (!settings.path().equals(uri.rawPath()) || request.uri().indexOf('#') >= 0) {
                return HttpResponseStatus.NOT_FOUND;
            }
            if (!uri.parameters().isEmpty()) {
                List<String> server = uri.parameters().get("server");
                if (uri.parameters().size() != 1 || server == null || server.size() != 1
                        || !List.of("beta", "development").contains(server.get(0))) {
                    return HttpResponseStatus.BAD_REQUEST;
                }
            }
        } catch (IllegalArgumentException e) {
            return HttpResponseStatus.BAD_REQUEST;
        }
        List<String> origins = request.headers().getAll(HttpHeaderNames.ORIGIN);
        if (origins.size() != 1 || !settings.allowedOrigins().contains(origins.get(0))) {
            return HttpResponseStatus.FORBIDDEN;
        }
        return null;
    }

    /**
     * Sends a bounded HTTP rejection while the connection still speaks HTTP.
     * Only the status is exposed, without echoing request headers or verified client metadata.
     *
     * @param ctx The upgrade context.
     * @param status The public response status.
     */
    private void reject(ChannelHandlerContext ctx, HttpResponseStatus status) {
        ByteBuf content = Unpooled.copiedBuffer(status.toString(), StandardCharsets.US_ASCII);
        var response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, content);
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, content.readableBytes());
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=us-ascii");
        response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    /**
     * Cancels the absolute HTTP/WebSocket handshake deadline if it is still registered.
     * All callers execute on the same channel event loop, including handler removal and disconnect callbacks.
     */
    private void cancelTimeout() {
        if (timeout != null) {
            timeout.cancel(false);
            timeout = null;
        }
    }

    /**
     * Preserves a bounded amount of early binary input until admission has completed.
     * <p>
     * HTTP upgrade and the first game data can arrive in one network read. This stage owns any early byte buffers
     * until it transfers them to the installed game decoder, or releases them when the connection fails. Both total
     * bytes and the number of buffered chunks are bounded; empty chunks retain no queue entries.
     *
     * @author lare96
     */
    private final class ReadyHandler extends ChannelInboundHandlerAdapter {

        /**
         * Creates the event-loop-owned input gate for this connection's upgrade and admission.
         */
        private ReadyHandler() {
        }

        /**
         * Owned references to decoded binary payloads received before the upgrade-complete event.
         */
        private final ArrayDeque<ByteBuf> earlyInput = new ArrayDeque<>();

        /**
         * Total readable bytes held by {@link #earlyInput}.
         */
        private int earlyBytes;

        /**
         * Becomes true only after successful admission and shared login pipeline installation.
         */
        private boolean ready;

        /**
         * Terminal pre-login failure that prevents buffering or admitting further input.
         */
        private boolean rejected;

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
            if (evt instanceof WebSocketServerProtocolHandler.HandshakeComplete && !ready && !rejected) {
                if (address == null) {
                    rejected = true;
                    releaseEarlyInput();
                    ctx.close();
                    return;
                }
                cancelTimeout();
                // Freeze the verified address before IdleClient caches it during shared pipeline installation.
                Client.setIpAddress(ctx.channel(), address);
                LoginResponse rejection = filter.admit(ctx.channel(), address);
                if (rejection != null) {
                    rejected = true;
                    releaseEarlyInput();
                    // Preserve the desktop filter's eight-byte preamble and encoded login rejection response.
                    ctx.pipeline().addLast("login-encoder", new LoginEncoder());
                    ctx.channel().write(ctx.alloc().buffer(Long.BYTES).writeLong(0));
                    ctx.channel().writeAndFlush(new LoginResponseMessage(rejection))
                            .addListener(ChannelFutureListener.CLOSE);
                    return;
                }
                gameInitializer.addGameHandlers(ctx.channel());
                ready = true;
                ctx.pipeline().remove(WebSocketUpgradeHandler.this);
                ctx.fireUserEventTriggered(evt);
                // Replay transport chunks in their original order; the shared decoder owns them after forwarding.
                boolean replayed = !earlyInput.isEmpty();
                ByteBuf bytes;
                while ((bytes = earlyInput.poll()) != null) {
                    earlyBytes -= bytes.readableBytes();
                    ctx.fireChannelRead(bytes);
                }
                if (replayed) {
                    ctx.fireChannelReadComplete();
                }
                return;
            }
            ctx.fireUserEventTriggered(evt);
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (ready) {
                ctx.fireChannelRead(msg);
            } else if (!rejected && msg instanceof ByteBuf bytes && earlyInput.size() < 64
                    && bytes.readableBytes() <= settings.maxFramePayloadLength() - earlyBytes) {
                if (bytes.isReadable()) {
                    earlyBytes += bytes.readableBytes();
                    earlyInput.add(bytes);
                } else {
                    bytes.release();
                }
            } else {
                rejected = true;
                ReferenceCountUtil.release(msg);
                releaseEarlyInput();
                ctx.close();
            }
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            releaseEarlyInput();
            ctx.fireChannelInactive();
        }

        @Override
        public void handlerRemoved(ChannelHandlerContext ctx) {
            releaseEarlyInput();
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            releaseEarlyInput();
            ctx.close();
        }

        /**
         * Releases every still-owned early payload when a handshake or connection is abandoned.
         * Clearing the queue makes repeated teardown callbacks safe.
         */
        private void releaseEarlyInput() {
            ByteBuf bytes;
            while ((bytes = earlyInput.poll()) != null) {
                bytes.release();
            }
            earlyBytes = 0;
        }
    }
}
