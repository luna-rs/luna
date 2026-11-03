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
 *
 * @author lare96
 */
final class WebSocketUpgradeHandler extends ChannelInboundHandlerAdapter {

    private final WebSocketSettings settings;
    private final LunaChannelFilter filter;
    private final LunaChannelInitializer gameInitializer;
    private ScheduledFuture<?> timeout;
    private String address;
    private boolean requestReceived;

    WebSocketUpgradeHandler(WebSocketSettings settings, LunaChannelFilter filter,
                            LunaChannelInitializer gameInitializer) {
        this.settings = settings;
        this.filter = filter;
        this.gameInitializer = gameInitializer;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
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
        ctx.fireChannelRead(request);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        ctx.close();
    }

    /**
     * Creates the post-upgrade admission handler for this connection.
     *
     * @return The handler installed after WebSocket framing.
     */
    ChannelInboundHandler readyHandler() {
        return new ReadyHandler();
    }

    /**
     * Checks the fixed endpoint and browser origin before allowing an HTTP upgrade.
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
     * Cancels the absolute HTTP/WebSocket handshake deadline.
     */
    private void cancelTimeout() {
        if (timeout != null) {
            timeout.cancel(false);
            timeout = null;
        }
    }

    /**
     * Preserves a bounded amount of early binary input until admission has completed.
     *
     * @author lare96
     */
    private final class ReadyHandler extends ChannelInboundHandlerAdapter {

        private final ArrayDeque<ByteBuf> earlyInput = new ArrayDeque<>();
        private int earlyBytes;
        private boolean ready;
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
                Client.setIpAddress(ctx.channel(), address);
                LoginResponse rejection = filter.admit(ctx.channel(), address);
                if (rejection != null) {
                    rejected = true;
                    releaseEarlyInput();
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
         * Releases retained data when a handshake or connection is abandoned.
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
