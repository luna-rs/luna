package io.luna.net;

import com.google.common.collect.ImmutableList;
import io.luna.net.session.Session;
import io.netty.util.AttributeKey;

/**
 * A model holding a set of constants relating to networking.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class LunaNetworkConstants {

    /**
     * The seconds that must elapse for a channel to be disconnected after no read operations.
     */
    public static final int READ_IDLE_SECONDS = 5;

    /**
     * The maximum amount of incoming messages per cycle.
     */
    public static final int MESSAGE_LIMIT = 15;

    /**
     * A list of exceptions that are ignored when received from Netty.
     */
    public static final ImmutableList<String> IGNORED_EXCEPTIONS = ImmutableList
        .of("An existing connection was forcibly closed by the remote host",
            "An established connection was aborted by the software in your host machine");

    /**
     * An attribute used to retrieve the session instance.
     */
    public static final AttributeKey<Session> SESSION_KEY = AttributeKey.valueOf("channel.SESSION_KEY");

    /**
     * A private constructor to discourage external instantiation.
     */
    private LunaNetworkConstants() {
    }
}
