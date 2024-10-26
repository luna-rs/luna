package io.luna;

import io.luna.util.benchmark.BenchmarkService;

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
     * The server will run in benchmark mode. Exactly like the {@code DEVELOPMENT} mode, except the
     * {@link BenchmarkService} is active.
     */
    BENCHMARK,

    /**
     * The server will run in production mode. Use this setting when running Luna on the live server.
     */
    PRODUCTION
}
