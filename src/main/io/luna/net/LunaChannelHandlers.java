package io.luna.net;

import io.netty.channel.ChannelHandler;

/**
 * A utility class that only contains immutable
 * {@link io.netty.channel.ChannelHandler}s that can be used simultaneously
 * across multiple pipelines.
 * 
 * @author lare96 <http://github.org/lare96>
 */
public final class LunaChannelHandlers {

    /**
     * Prepares channels for registration.
     */
    public static final ChannelHandler CHANNEL_INITIALIZER = new LunaChannelInitializer();

    /**
     * Handles upstream messages from Netty.
     */
    static final ChannelHandler UPSTREAM_HANDLER = new LunaUpstreamHandler();

    /**
     * Encodes and sends downstream messages to the client.
     */
    static final ChannelHandler MESSAGE_ENCODER = null;

    /**
     * Decodes the handshake section of the login protocol.
     */
    static final ChannelHandler HANDSHAKE_DECODER = null;

    /**
     * Decodes the rest of the login protocol.
     */
    static final ChannelHandler POST_HANDSHAKE_DECODER = null;

    /**
     * A private constructor to discourage external instantiation.
     */
    private LunaChannelHandlers() {}
}
