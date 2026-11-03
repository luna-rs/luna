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
 *
 * @author lare96
 */
public final class WebSocketChannelInitializer extends ChannelInitializer<SocketChannel> {

    private final LunaChannelInitializer gameInitializer;
    private final LunaChannelFilter channelFilter;
    private final WebSocketSettings settings;

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
                .checkStartsWith(true)
                .allowExtensions(false)
                .maxFramePayloadLength(settings.maxFramePayloadLength())
                .handshakeTimeoutMillis(settings.handshakeTimeoutMillis())
                .forceCloseTimeoutMillis(1000)
                .dropPongFrames(true)
                .build();
        channel.pipeline().addLast("websocket-write-limit", new WebSocketWriteLimitHandler(settings.maxPendingOutboundBytes()));
        channel.pipeline().addLast("websocket-http", new HttpServerCodec());
        channel.pipeline().addLast("websocket-http-aggregator", new HttpObjectAggregator(settings.maxHttpContentLength()));
        channel.pipeline().addLast("websocket-upgrade", upgrade);
        channel.pipeline().addLast("websocket-protocol", new WebSocketServerProtocolHandler(protocol));
        channel.pipeline().addLast("websocket-frame-aggregator", new WebSocketFrameAggregator(settings.maxFramePayloadLength()));
        channel.pipeline().addLast("websocket-game-codec", new WebSocketGameCodec(settings.maxPendingOutboundBytes()));
        channel.pipeline().addLast("websocket-ready", upgrade.readyHandler());
    }
}
