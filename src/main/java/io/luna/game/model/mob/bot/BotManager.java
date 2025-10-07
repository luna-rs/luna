package io.luna.game.model.mob.bot;

import api.bot.BotScript;
import io.luna.game.model.mob.bot.brain.BotPersonalityManager;
import io.luna.game.model.mob.bot.brain.BotPersonalityManager.PersonalityTemplate;
import io.luna.game.model.mob.bot.script.BotScriptManager;
import io.luna.game.model.mob.bot.speech.BotGeneralSpeechPool;
import io.luna.game.model.mob.bot.speech.BotSpeechManager;

/**
 * Coordinates the high-level data systems related to automated bot behavior.
 * <p>
 * This manager exposes access to the speech, script, and personality managers that collectively hold global data
 * relevant to {@link Bot} instances. {@link #load()} must be invoked during server initialization to populate reusable
 * assets such as speech pools and personality templates.
 *
 * @author lare96
 */
public final class BotManager {

    /**
     * Holds the global {@link BotGeneralSpeechPool} and handles speech injectors.
     */
    private final BotSpeechManager speechManager = new BotSpeechManager();

    /**
     * Manages persistence for {@link BotScript} instances.
     */
    private final BotScriptManager scriptManager = new BotScriptManager();

    /**
     * Supplies {@link PersonalityTemplate} definitions for bots.
     */
    private final BotPersonalityManager personalityManager = new BotPersonalityManager();

    /**
     * Initializes bot subsystems and loads reusable data sets.
     */
    public void load() {
        speechManager.load();
        personalityManager.loadTemplates();
    }

    /**
     * @return The speech manager.
     */
    public BotSpeechManager getSpeechManager() {
        return speechManager;
    }

    /**
     * @return The script manager.
     */
    public BotScriptManager getScriptManager() {
        return scriptManager;
    }

    /**
     * @return The personality manager.
     */
    public BotPersonalityManager getPersonalityManager() {
        return personalityManager;
    }
}
