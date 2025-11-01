package io.luna.game.model.mob.bot;

import io.luna.game.event.Event;
import io.luna.game.event.EventListenerPipeline;

// listens for world events that bots can react to, bots can have multiple injectors
public interface BotContextInjector {

    /**
     * Handles an incoming {@link Event} routed from the global {@link EventListenerPipeline}.
     * <p>
     * Subclasses implement this method to detect relevant events and handle appropriate actions for the bot.
     *
     * @param event The event received from the world.
     */
    void onEvent(Event event);
}
