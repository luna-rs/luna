package io.luna.game.model.mob.bot;

import api.bot.BotScript;
import io.luna.game.model.World;
import io.luna.game.model.mob.bot.brain.BotPersonalityManager;
import io.luna.game.model.mob.bot.injection.BotContextInjector;
import io.luna.game.model.mob.bot.injection.BotContextInjectorManager;
import io.luna.game.model.mob.bot.schedule.BotScheduleService;
import io.luna.game.model.mob.bot.script.BotScriptManager;
import io.luna.game.model.mob.bot.speech.BotGeneralSpeechPool;

/**
 * Coordinates the global systems used by automated bot behavior.
 * <p>
 * This manager acts as the central access point for bot-related subsystems that are shared across all {@link Bot}
 * instances. These subsystems load and expose reusable data such as bot scripts, personality definitions, speech data,
 * and context injectors.
 * <p>
 * {@link #load()} should be called during server startup before bots are created or assigned behavior.
 *
 * @author lare96
 */
public final class BotManager {

    /**
     * Manages loading, lookup, and persistence for {@link BotScript} instances.
     */
    private final BotScriptManager scriptManager = new BotScriptManager();

    /**
     * Manages {@link BotContextInjector} registrations used to attach contextual behavior to bots.
     */
    private final BotContextInjectorManager injectorManager = new BotContextInjectorManager();

    /**
     * Manages personality data used to generate or assign bot behavioral traits.
     */
    private final BotPersonalityManager personalityManager = new BotPersonalityManager();

    /**
     * The generic speech pool.
     */
    private final BotGeneralSpeechPool generalSpeechPool = new BotGeneralSpeechPool();

    /**
     * The bot scheduler.
     */
    private final BotScheduleService scheduleService;

    /**
     * Creates a new {@link BotManager}.
     *
     * @param world The world.
     */
    public BotManager(World world) {
        scheduleService = new BotScheduleService(world);
    }

    /**
     * Loads bot subsystem data required at runtime.
     * <p>
     * This should be called once during server initialization. Managers that rely on external data files or reusable
     * global definitions should perform their loading here.
     */
    public void load() {
        personalityManager.load();
        generalSpeechPool.load();
        scheduleService.startAsync();
    }

    /**
     * @return The bot script manager.
     */
    public BotScriptManager getScriptManager() {
        return scriptManager;
    }

    /**
     * @return The bot context injector manager.
     */
    public BotContextInjectorManager getInjectorManager() {
        return injectorManager;
    }

    /**
     * @return The bot personality manager.
     */
    public BotPersonalityManager getPersonalityManager() {
        return personalityManager;
    }

    /**
     * @return The generic speech pool.
     */
    public BotGeneralSpeechPool getGeneralPool() {
        return generalSpeechPool;
    }

    /**
     * @return The bot scheduler.
     */
    public BotScheduleService getScheduleService() {
        return scheduleService;
    }
}