package io.luna.net;

import io.luna.net.codec.game.MessageEncoder;
import io.luna.net.codec.login.LoginDecoder;
import io.netty.channel.ChannelHandler;

/**
 * A utility class that only contains immutable
 * {@linkplain io.netty.channel.ChannelHandler channel handlers} that can be
 * used simultaneously across multiple pipelines.
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
    static final ChannelHandler MESSAGE_ENCODER = new MessageEncoder();

    /**
     * Decodes the entire login protocol.
     */
    static final ChannelHandler LOGIN_DECODER = new LoginDecoder();

    /**
     * Filters channels based on the amount of active connections they have.
     */
    static final ChannelHandler CHANNEL_FILTER = new LunaChannelFilter();

    /**
     * A private constructor to discourage external instantiation.
     */
    private LunaChannelHandlers() {}
}
