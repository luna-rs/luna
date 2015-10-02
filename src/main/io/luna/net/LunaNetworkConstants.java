package io.luna.net;

import io.luna.net.session.Session;
import io.netty.util.AttributeKey;
import io.netty.util.ResourceLeakDetector.Level;

import com.google.common.collect.ImmutableList;

/**
 * A utility class that only contains Netty constants.
 * 
 * @author lare96 <http://github.org/lare96>
 */
public final class LunaNetworkConstants {

    /**
     * The resource leak detection level, should be {@code PARANOID} in a
     * development environment and {@code DISABLED} in a production environment.
     */
    public static final Level RESOURCE_LEAK_DETECTION = Level.PARANOID;

    /**
     * The port that the server will be bound on.
     */
    public static final int PORT = 43594;

    /**
     * The amount of {@code SECONDS} that must elapse for a channel to be
     * disconnected after no read operations.
     */
    public static final int READ_IDLE_SECONDS = 5;

    /**
     * The maximum amount of connections allowed per channel.
     */
    public static final int CONNECTION_LIMIT = 2;

    /**
     * The maximum amount of incoming messages per cycle.
     */
    public static final int MESSAGE_LIMIT = 15;

    /**
     * The preferred ports for the user to use, a warning will be printed if
     * these ports aren't used.
     */
    public static final ImmutableList<Integer> PREFERRED_PORTS = ImmutableList.of(43594, 5555);

    /**
     * A list of exceptions that are ignored when received from Netty.
     */
    public static final ImmutableList<String> IGNORED_EXCEPTIONS = ImmutableList.of("An existing connection was forcibly closed by the remote host",
        "An established connection was aborted by the software in your host machine");

    /**
     * An {@link AttributeKey} that is used to retrieve the session instance
     * from the attribute map of a {@link Channel}.
     */
    public static final AttributeKey<Session> SESSION_KEY = AttributeKey.valueOf("session.KEY");

    /**
     * A private constructor to discourage external instantiation.
     */
    private LunaNetworkConstants() {}
}
