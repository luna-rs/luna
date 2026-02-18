package io.luna;

/**
 * Server runtime mode. Used to toggle behavior between local/beta and live deployments.
 * <p>
 * This is typically consumed by configuration (e.g., {@code luna.json}) to:
 * <ul>
 *   <li>Enable more verbose logging and diagnostics in development</li>
 *   <li>Enable stricter safety / performance defaults in production</li>
 *   <li>Gate hot-reload, debug commands, or experimental features</li>
 * </ul>
 *
 * @author lare96
 */
public enum LunaRuntime {

    /**
     * Development / beta mode. Intended for local testing and staging environments.
     */
    DEVELOPMENT,

    /**
     * Production mode. Intended for live servers.
     */
    PRODUCTION
}
