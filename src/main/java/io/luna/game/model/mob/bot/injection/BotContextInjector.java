package io.luna.game.model.mob.bot.injection;

import io.luna.game.event.Event;
import io.luna.game.event.EventListenerPipeline;
import io.luna.game.model.mob.bot.Bot;

/**
 * A handler that injects contextual reactions or behaviors into {@link Bot} instances in response to
 * {@link InjectableEvent}s broadcast from the global {@link EventListenerPipeline}.
 * <p>
 * Each {@code BotContextInjector} defines a distinct domain of reactive behavior such as speech, combat, movement,
 * or trading. Multiple injectors can be registered at once through the {@link BotContextInjectorManager}, allowing
 * bots to respond dynamically to a wide range of in-game events without tightly coupling their logic.
 * <p>
 * The engine routes all injectable world events through this interface. Each injector decides independently whether a
 * given event is relevant to the provided {@link Bot}, and if so, performs the necessary actions (for example, making
 * the bot reply to a chat message, reposition itself, or initiate combat). The injector returns {@code true} if it has
 * handled the event completely, signaling the manager to stop further propagation to subsequent injectors for that bot.
 * <p>
 * Injectors are typically stateless and lightweight; they should rely on the bot’s internal state and contextual
 * world data rather than storing long-term fields. This allows for highly modular, composable AI systems where new
 * behaviors can be introduced simply by registering new injectors.
 *
 * @author lare96
 * @see BotContextInjectorManager
 * @see InjectableEvent
 * @see Bot
 * @see Event
 * @see EventListenerPipeline
 */
public interface BotContextInjector {

    /**
     * Processes an incoming {@link InjectableEvent} to determine if and how the {@link Bot} should react.
     * <p>
     * Returning {@code true} indicates that the event has been fully handled and should not be passed to
     * subsequent injectors for this bot.
     *
     * @param bot The {@link Bot} receiving the event.
     * @param event The {@link InjectableEvent} being processed.
     * @return {@code true} if this injector has handled the event and further propagation should stop,
     * or {@code false} to allow subsequent injectors to process it.
     */
    boolean onEvent(Bot bot, InjectableEvent event);
}
