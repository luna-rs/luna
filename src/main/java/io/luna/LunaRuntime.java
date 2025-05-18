package io.luna;

/**
 * An enumerated type representing the different modes Luna can be ran in.
 *
 * @author lare96
 */
public enum LunaRuntime {

    /**
     * The server will run in development mode. Use this setting when running Luna locally and on beta servers.
     */
    DEVELOPMENT,

    /**
     * The server will run in production mode. Use this setting when running Luna on the live server.
     */
    PRODUCTION
}
