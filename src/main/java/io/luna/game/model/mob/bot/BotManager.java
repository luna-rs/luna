package io.luna.game.model.mob.bot;

import api.bot.BotScript;
import io.luna.game.model.mob.bot.injection.BotContextInjector;
import io.luna.game.model.mob.bot.injection.BotContextInjectorManager;
import io.luna.game.model.mob.bot.movement.BotMovementManager;
import io.luna.game.model.mob.bot.movement.BotMovementStack;
import io.luna.game.model.mob.bot.script.BotScriptManager;

/**
 * Coordinates the high-level data systems related to automated bot behavior.
 * <p>
 * This manager exposes access to the managers that collectively hold global data relevant to {@link Bot} instances.
 * {@link #load()} must be invoked during server initialization to populate reusable assets.
 *
 * @author lare96
 */
public final class BotManager {

    /**
     * Manages persistence for {@link BotScript} instances.
     */
    private final BotScriptManager scriptManager = new BotScriptManager();

    /**
     * Manages the {@link BotMovementStack} for bots.
     */
    private final BotMovementManager movementManager = new BotMovementManager();

    /**
     * Manages the {@link BotContextInjector} listeners for bots.
     */
    private final BotContextInjectorManager injectorManager = new BotContextInjectorManager();

    /**
     * Initializes bot subsystems and loads reusable data sets.
     */
    public void load() {
    }

    /**
     * @return The script manager.
     */
    public BotScriptManager getScriptManager() {
        return scriptManager;
    }

    /**
     * @return The movement manager.
     */
    public BotMovementManager getMovementManager() {
        return movementManager;
    }

    /**
     * @return Manages the {@link BotContextInjector} listeners for bots.
     */
    public BotContextInjectorManager getInjectorManager() {
        return injectorManager;
    }
}
