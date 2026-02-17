package io.luna;

import game.skill.SkillsSettings;
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
    private final SkillsSettings skills;

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

    /**
     * The skills settings.
     */
    public SkillsSettings skills() {
        return skills;
    }

    // Never called
    private LunaSettings(GameSettings game, DatabaseSettings database, LoggingSettings logging, SkillsSettings skills) {
        this.game = game;
        this.database = database;
        this.logging = logging;
        this.skills = skills;
    }
}
