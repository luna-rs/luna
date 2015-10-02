package io.luna.net;

import io.luna.net.codec.login.LoginEncoder;
import io.netty.channel.ChannelHandler;

/**
 * A utility class that only contains immutable {@link ChannelHandler}s that can
 * be used simultaneously across multiple pipelines.
 * 
 * @author lare96 <http://github.org/lare96>
 */
public final class LunaChannelHandlers {

    /**
     * Handles upstream messages from Netty.
     */
    static final ChannelHandler UPSTREAM_HANDLER = new LunaUpstreamHandler();

    /**
     * Encodes the login response.
     */
    static final ChannelHandler LOGIN_ENCODER = new LoginEncoder();

    /**
     * Filters channels based on the amount of active connections they have.
     */
    static final ChannelHandler CHANNEL_FILTER = new LunaChannelFilter();

    /**
     * A private constructor to discourage external instantiation.
     */
    private LunaChannelHandlers() {}
}
