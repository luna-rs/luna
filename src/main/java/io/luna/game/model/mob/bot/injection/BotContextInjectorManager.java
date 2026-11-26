package io.luna.game.model.mob.bot.injection;

import com.google.common.collect.ImmutableList;
import io.luna.game.event.Event;
import io.luna.game.event.impl.InjectableEvent;
import io.luna.game.model.Locatable;
import io.luna.game.model.Position;
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
 * {@link #setInjectors(List)}. Bots are processed inside the player loop, so an event can be posted after some bots
 * have already had their turn. Events are therefore kept until the end of the following tick, and each bot keeps a
 * cursor ({@link Bot#getNextInjectedEvent()}) so every event is offered to it once.
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
     * The events posted during this tick and the previous one, oldest first.
     */
    private final List<InjectableEvent> events = new ArrayList<>();

    /**
     * The sequence number of the first entry in {@link #events}.
     */
    private long firstSequence;

    /**
     * The sequence number of the first event posted during the current tick.
     */
    private long tickStartSequence;

    /**
     * Dispatches every {@link InjectableEvent} this bot has not yet been offered, while it has a human observer.
     * Human presence is read from the existing local-human view without a world or chunk scan. Events skipped
     * because no human is watching are not offered again later.
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
        int start = (int) Math.max(0, bot.getNextInjectedEvent() - firstSequence);
        int end = events.size();
        bot.setNextInjectedEvent(firstSequence + end);
        if (start >= end || bot.getLocalHumans().isEmpty()) {
            return;
        }
        for (int index = start; index < end; index++) {
            InjectableEvent event = events.get(index);
            Locatable locatable = event.contextLocatable(bot);
            if (locatable == null || locatable.equals(bot)) {
                continue;
            }

            int radius = event.contextRadius(bot);
            Position position = locatable.abs();
            if (position == null) {
                continue;
            }
            if (radius == -1 || !bot.getPosition().isWithinDistance(position, radius)) {
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
     * Adds a new {@link InjectableEvent} to the pending dispatch buffer, unless
     * {@link InjectableEvent#isInjectable()} rules it out.
     * <p>
     * Each bot is offered the event on its next call to {@link #injectEvents(Bot)}.
     *
     * @param event The injectable event to queue.
     */
    public void addEvent(InjectableEvent event) {
        if (event.isInjectable()) {
            events.add(event);
        }
    }

    /**
     * Drops the events posted before this tick. Every bot has had a turn since they were posted.
     * <p>
     * This should be called once per tick after all bots have been processed.
     */
    public void advanceTick() {
        events.subList(0, (int) (tickStartSequence - firstSequence)).clear();
        firstSequence = tickStartSequence;
        tickStartSequence = firstSequence + events.size();
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
