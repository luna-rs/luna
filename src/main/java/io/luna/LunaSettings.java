package io.luna;

import io.luna.game.GameSettings;
import io.luna.util.SqlConnectionPool.DatabaseSettings;
import io.luna.util.logging.LoggingSettings;

/**
 * Holds settings parsed in the {@code ./data/luna.json} file.
 *
 * @author lare96
 */
public final class LunaSettings {

    private final GameSettings game;
    private final DatabaseSettings database;
    private final LoggingSettings logging;

    /**
     * The game settings.
     */
    public GameSettings game() {
        return game;
    }

    /**
     * The database settings.
     */
    public DatabaseSettings database() {
        return database;
    }

    /**
     * The logging settings.
     */
    public LoggingSettings logging() {
        return logging;
    }

    // Never called
    private LunaSettings(GameSettings game, DatabaseSettings database, LoggingSettings logging) {
        this.game = game;
        this.database = database;
        this.logging = logging;
    }
}
