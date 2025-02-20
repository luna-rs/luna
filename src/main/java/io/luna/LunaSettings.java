package io.luna;

import io.luna.game.GameSettings;
import io.luna.game.model.mob.bot.BotSettings;
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

    // Never called
    private LunaSettings(GameSettings game, LoggingSettings logging, BotSettings bots) {
        this.game = game;
        this.logging = logging;
        this.bots = bots;
    }
}
