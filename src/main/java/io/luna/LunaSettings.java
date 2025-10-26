package io.luna;

import io.luna.game.GameSettings;
import io.luna.game.model.mob.bot.BotSettings;
import io.luna.util.SqlConnectionPool.DatabaseSettings;
import io.luna.util.logging.LoggingSettings;

/**
 * Holds settings parsed in the {@code ./data/luna.json} file.
 *
 * @author lare96
 */
public final class LunaSettings {

    private final GameSettings game;
    private final LoggingSettings logging;
    private final BotSettings bots;
    private final DatabaseSettings database;

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
     * The bot settings.
     */
    public BotSettings bots() {
        return bots;
    }

    /**
     * The database settings.
     */
    public DatabaseSettings database() {
        return database;
    }

    // Never called
    private LunaSettings(GameSettings game, LoggingSettings logging, BotSettings bots, DatabaseSettings database) {
        this.game = game;
        this.logging = logging;
        this.bots = bots;
        this.database = database;
    }
}
