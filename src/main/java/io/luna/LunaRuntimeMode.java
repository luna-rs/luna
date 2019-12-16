package io.luna;

/**
 * An enumerated type representing the different modes Luna can be ran in.
 *
 * @author lare96 <http://github.org/lare96>
 */
public enum LunaRuntimeMode {

    /**
     * The server will run in development mode. Use this setting when running Luna locally and on beta servers.
     */
    DEVELOPMENT,

    /**
     * The server will run in benchmark mode. Exactly like the {@code DEVELOPMENT} mode, except cycle time debug
     * statements are printed and login positions are scattered.
     */
    BENCHMARK,

    /**
     * The server will run in production mode. Use this setting when running Luna on the live server.
     */
    PRODUCTION
}
