package io.luna;

import io.luna.game.GameSettings;
import io.luna.util.logging.LoggingSettings;
import io.luna.util.benchmark.BenchmarkSettings;

/**
 * Holds settings parsed in the {@code ./data/luna.json} file.
 *
 * @author lare96
 */
public final class LunaSettings {

    private final GameSettings game;
    private final LoggingSettings logging;
    private final BenchmarkSettings benchmark;

    /**
     * The game settings.
     */
    public GameSettings game() {
        return game;
    }

    /**
     * The logging settings.
     */
    public LoggingSettings logging() {
        return logging;
    }

    /**
     * The benchmark settings. Only active when {@link GameSettings#runtimeMode()} is equal to
     * {@link LunaRuntime#BENCHMARK}.
     */
    public BenchmarkSettings benchmark() {
        return benchmark;
    }

    // Never called
    private LunaSettings(GameSettings game, LoggingSettings logging, BenchmarkSettings benchmark) {
        this.game = game;
        this.logging = logging;
        this.benchmark = benchmark;
    }
}
