package io.luna.net.websocket;

import io.luna.LunaContext;
import io.luna.net.LunaChannelFilter;
import io.luna.net.LunaChannelInitializer;
import io.luna.net.msg.GameMessageRepository;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketFrameAggregator;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolConfig;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;

/**
 * Adds a browser transport before the same login and game codecs used by desktop clients.
 * <p>
 * Each accepted socket receives its own upgrade, framing, queue accounting, and admission handlers. Their mutable
 * state belongs to that socket's Netty event loop. The shared game pipeline is installed only after HTTP upgrade
 * validation and connection admission; packet decoding, authentication, and game-thread dispatch remain unchanged.
 *
 * @author lare96
 */
public final class WebSocketChannelInitializer extends ChannelInitializer<SocketChannel> {

    /**
     * Installs the existing login and game handlers after browser transport admission.
     */
    private final LunaChannelInitializer gameInitializer;

    /**
     * Applies the same connection limits and blacklist as the desktop listener.
     */
    private final LunaChannelFilter channelFilter;

    /**
     * Listener configuration validated before the server binds either network transport.
     */
    private final WebSocketSettings settings;

    /**
     * Creates the browser pipeline factory using the server's existing protocol dependencies.
     *
     * @param context The game context used by the shared login decoder.
     * @param channelFilter The admission policy shared with desktop connections.
     * @param repository The existing game packet repository.
     * @param settings The validated browser listener settings.
     */
    public WebSocketChannelInitializer(LunaContext context, LunaChannelFilter channelFilter,
                                       GameMessageRepository repository, WebSocketSettings settings) {
        this.gameInitializer = new LunaChannelInitializer(context, channelFilter, repository);
        this.channelFilter = channelFilter;
        this.settings = settings;
    }

    @Override
    protected void initChannel(SocketChannel channel) {
        WebSocketUpgradeHandler upgrade = new WebSocketUpgradeHandler(settings, channelFilter, gameInitializer);
        var protocol = WebSocketServerProtocolConfig.newBuilder()
                .websocketPath(settings.path())
                // Netty otherwise compares the entire URI and rejects the supported ?server=beta query.
                // The preceding upgrade guard still requires an exact raw path and validates every parameter.
                .checkStartsWith(true)
                .allowExtensions(false)
                .maxFramePayloadLength(settings.maxFramePayloadLength())
                .handshakeTimeoutMillis(settings.handshakeTimeoutMillis())
                .forceCloseTimeoutMillis(1000)
                .dropPongFrames(true)
                .build();
        // Keep the wire limit before both HTTP and WebSocket encoders so control replies are counted too.
        channel.pipeline().addLast("websocket-write-limit", new WebSocketWriteLimitHandler(settings.maxPendingOutboundBytes()));
        channel.pipeline().addLast("websocket-http", new HttpServerCodec());
        channel.pipeline().addLast("websocket-http-aggregator", new HttpObjectAggregator(settings.maxHttpContentLength()));
        channel.pipeline().addLast("websocket-upgrade", upgrade);
        // Netty handles ping, pong, and close frames before fragmented game data is reassembled.
        channel.pipeline().addLast("websocket-protocol", new WebSocketServerProtocolHandler(protocol));
        channel.pipeline().addLast("websocket-frame-aggregator", new WebSocketFrameAggregator(settings.maxFramePayloadLength()));
        channel.pipeline().addLast("websocket-game-codec", new WebSocketGameCodec(settings.maxPendingOutboundBytes()));
        channel.pipeline().addLast("websocket-ready", upgrade.readyHandler());
    }
}
