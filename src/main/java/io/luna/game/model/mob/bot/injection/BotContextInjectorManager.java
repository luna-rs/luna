package io.luna.game.model.mob.bot.injection;

import com.google.common.collect.ImmutableList;
import io.luna.game.event.Event;
import io.luna.game.model.Locatable;
import io.luna.game.model.mob.bot.Bot;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages the collection of {@link BotContextInjector} instances responsible for injecting contextual behavior
 * into {@link Bot} entities in response to {@link InjectableEvent}s.
 * <p>
 * The {@code BotContextInjectorManager} acts as the central dispatcher for all active context injectors.
 * Each injector in the chain receives the opportunity to handle incoming events. Once an injector successfully
 * processes an event (that is, {@link BotContextInjector#onEvent(Bot, InjectableEvent)} returns {@code true}),
 * iteration halts immediately for that specific event.
 * <p>
 * Events marked as {@link InjectableEvent}s are accumulated during the tick and then distributed to all bots
 * during the injection phase via {@link #injectEvents(Bot)}. Events are only injected into bots located within
 * the event’s spatial context, defined by {@link InjectableEvent#contextLocatable(Bot)} and
 * {@link InjectableEvent#contextRadius(Bot)}. This ensures that bots only react to events occurring nearby,
 * improving both realism and scalability.
 * <p>
 * The internal injector list is stored as an immutable snapshot for thread safety and atomic updates through
 * {@link #setInjectors(List)}. The pending event buffer is cleared at the end of each tick using {@link #clearEvents()}.
 *
 * @author lare96
 * @see BotContextInjector
 * @see InjectableEvent
 * @see Bot
 * @see Event
 */
public final class BotContextInjectorManager {

    /**
     * The immutable list of all registered {@link BotContextInjector}s.
     */
    private volatile ImmutableList<BotContextInjector> injectorList = ImmutableList.of();

    /**
     * The temporary buffer of {@link InjectableEvent}s awaiting dispatch.
     * <p>
     * This list is populated throughout the tick and flushed at the end of each tick cycle
     * using {@link #clearEvents()}.
     */
    private final List<InjectableEvent> pendingDispatch = new ArrayList<>();

    /**
     * Dispatches all {@link InjectableEvent}s in {@link #pendingDispatch} to this bot.
     * <p>
     * For each event, the spatial context is checked:
     * <ul>
     *   <li>If {@link InjectableEvent#contextLocatable(Bot)} returns {@code null}, the event is skipped.</li>
     *   <li>If {@link InjectableEvent#contextRadius(Bot)} returns {@code -1}, the event is never viewable and is skipped.</li>
     *   <li>Otherwise, the bot must be within the event’s contextual radius to process it.</li>
     * </ul>
     * If the event passes all filters, each injector is invoked sequentially until one successfully handles the
     * event (returns {@code true}).
     *
     * @param bot The bot receiving the event injection pass.
     */
    public void injectEvents(Bot bot) {
        for (InjectableEvent event : pendingDispatch) {
            Locatable locatable = event.contextLocatable(bot);
            if (locatable == null || locatable.equals(bot)) {
                continue;
            }

            int radius = event.contextRadius(bot);
            if (radius == -1 || !bot.getPosition().isWithinDistance(locatable.absLocation(), radius)) {
                continue;
            }

            for (BotContextInjector injector : injectorList) {
                if (injector.onEvent(bot, event)) {
                    break;
                }
            }
        }
    }

    /**
     * Adds a new {@link InjectableEvent} to the pending dispatch buffer.
     * <p>
     * The event will be delivered to eligible bots on the next call to {@link #injectEvents(Bot)}.
     *
     * @param event The injectable event to queue.
     */
    public void addEvent(InjectableEvent event) {
        pendingDispatch.add(event);
    }

    /**
     * Clears all events in the pending dispatch buffer.
     * <p>
     * This should be called once per tick after all bots have been processed to prevent duplicate event injections
     * on the following cycle.
     */
    public void clearEvents() {
        pendingDispatch.clear();
    }

    /**
     * Atomically replaces the current set of {@link BotContextInjector}s.
     * <p>
     * The provided list is defensively copied into an immutable snapshot to prevent external modification and
     * ensure consistent iteration semantics across threads.
     *
     * @param injectors The new list of injectors to register.
     */
    public void setInjectors(List<BotContextInjector> injectors) {
        injectorList = ImmutableList.copyOf(injectors);
    }
}
