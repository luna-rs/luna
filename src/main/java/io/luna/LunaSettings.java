package io.luna;

import game.skill.SkillsSettings;
import io.luna.game.GameSettings;
import io.luna.util.SqlConnectionPool.DatabaseSettings;
import io.luna.util.logging.LoggingSettings;

/**
 * Root settings model parsed from {@code ./data/luna.json}.
 * <p>
 * This class is a simple configuration aggregate and is treated as immutable after load. Instances are created via
 * Gson deserialization.
 *
 * @author lare96
 */
public final class LunaSettings {

    /**
     * Game-server settings (port, leak detection, runtime values, etc.).
     */
    private final GameSettings game;

    /**
     * Database settings used by the SQL connection pool (if enabled).
     */
    private final DatabaseSettings database;

    /**
     * Logging settings that configure Log4j behavior/output.
     */
    private final LoggingSettings logging;

    /**
     * Skill-related tuning/parameters.
     */
    private final SkillsSettings skills;

    /**
     * Returns game-server settings.
     */
    public GameSettings game() {
        return game;
    }

    /**
     * Returns database settings.
     */
    public DatabaseSettings database() {
        return database;
    }

    /**
     * Returns logging settings.
     */
    public LoggingSettings logging() {
        return logging;
    }

    /**
     * Returns skill settings.
     */
    public SkillsSettings skills() {
        return skills;
    }

    /**
     * Private constructor used by Gson. Not invoked directly.
     */
    private LunaSettings(GameSettings game, DatabaseSettings database, LoggingSettings logging, SkillsSettings skills) {
        this.game = game;
        this.database = database;
        this.logging = logging;
        this.skills = skills;
    }
}
