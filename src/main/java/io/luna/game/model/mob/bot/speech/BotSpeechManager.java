package io.luna.game.model.mob.bot.speech;

import io.luna.game.event.Event;

import java.util.ArrayList;
import java.util.List;

/**
 * Central manager for all bot speech systems.
 * <p>
 * This class coordinates:
 * <ul>
 *     <li>The {@link BotGeneralSpeechPool}, which provides idle and general-purpose phrases.</li>
 *     <li>All registered {@link BotSpeechContextInjector} instances, which listen for game events and
 *     dynamically inject context-specific speech (e.g., reacting to combat, drops, or player interactions).</li>
 * </ul>
 * It acts as the single entry point for initializing and routing all bot speech activity.
 *
 * @author lare96
 */
public final class BotSpeechManager {

    /**
     * The shared pool of general speech phrases.
     */
    private final BotGeneralSpeechPool generalSpeechPool = new BotGeneralSpeechPool();

    /**
     * The list of active {@link BotSpeechContextInjector} instances.
     */
    private final List<BotSpeechContextInjector<?>> speechInjectors = new ArrayList<>();

    /**
     * Loads the {@link BotGeneralSpeechPool} and starts all registered {@link BotSpeechContextInjector}s.
     * <p>
     * This should be called once during server startup, after all injectors have been registered.
     */
    public void load() {
        generalSpeechPool.load();
        for (BotSpeechContextInjector<?> injector : speechInjectors) {
            injector.start();
        }
    }

    /**
     * Registers a new {@link BotSpeechContextInjector} with this manager.
     * <p>
     * Injectors added here will automatically receive events through {@link #handleInjectors(Event)}.
     *
     * @param injector The injector to register.
     */
    public void addInjector(BotSpeechContextInjector<?> injector) {
        speechInjectors.add(injector);
    }

    /**
     * Routes a given {@link Event} to all registered {@link BotSpeechContextInjector}s.
     * <p>
     * This allows injectors to react to gameplay events and queue speech for their associated bots.
     *
     * @param event The event to broadcast.
     */
    public void handleInjectors(Event event) {
        for (BotSpeechContextInjector<?> injector : speechInjectors) {
            injector.onEvent(event);
        }
    }

    /**
     * Returns the shared {@link BotGeneralSpeechPool}.
     * <p>
     * This pool stores the preloaded general phrase categories used by all bots.
     *
     * @return The general speech pool.
     */
    public BotGeneralSpeechPool getGeneralSpeechPool() {
        return generalSpeechPool;
    }
}
