package io.luna.game.model.mob.bot.speech;

import io.luna.game.event.Event;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles all speech injectors and loads the general speech pool.
 *
 * @author lare96
 */
public final class BotSpeechManager {

    /**
     * The general speech pool.
     */
    private final BotGeneralSpeechPool generalSpeechPool = new BotGeneralSpeechPool();

    /**
     * The speech injectors.
     */
    private final List<BotSpeechContextInjector<?>> speechInjectors = new ArrayList<>();

    /**
     * Loads the general speech pool and starts all injectors
     */
    public void load() {
        generalSpeechPool.load();
        for (BotSpeechContextInjector<?> injector : speechInjectors) {
            injector.start();
        }
    }

    /**
     * Registers an injector with this manager.
     *
     * @param injector The injector.
     */
    public void addInjector(BotSpeechContextInjector<?> injector) {
        speechInjectors.add(injector);
    }

    /**
     * Posts events to all registered {@link BotSpeechContextInjector} types.
     *
     * @param event The event to post.
     */
    public void handleInjectors(Event event) {
        for (BotSpeechContextInjector<?> injector : speechInjectors) {
            injector.onEvent(event);
        }
    }
}
