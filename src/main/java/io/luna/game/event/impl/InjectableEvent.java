package io.luna.game.event.impl;

import io.luna.game.event.Event;
import io.luna.game.model.Locatable;
import io.luna.game.model.Position;
import io.luna.game.model.mob.bot.Bot;
import io.luna.game.model.mob.bot.injection.BotContextInjector;
import io.luna.game.model.mob.bot.injection.BotContextInjectorManager;

/**
 * Represents an {@link Event} that can be injected into a {@link Bot} through the {@link BotContextInjectorManager}.
 * <p>
 * An {@code InjectableEvent} exposes a spatial context that determines whether it is relevant to a given bot. This
 * allows the event system to filter out events that occur too far away or are otherwise not applicable, improving
 * both realism and performance in large-scale simulations.
 * <p>
 * Implementations may choose to vary the context location or radius per bot to simulate personalized perception; for
 * instance, bots with higher awareness could have an expanded viewing range for certain events.
 *
 * @author lare96
 * @see BotContextInjectorManager
 * @see BotContextInjector
 * @see Bot
 * @see Event
 */
public interface InjectableEvent {

    /**
     * Returns the {@link Locatable} that defines the spatial context of this event for the specified {@link Bot}.
     * The context location is used to determine proximity and visibility when deciding if the event should be
     * injected.
     * <p>
     * A return value of {@code null} indicates that the event has no spatial context and should not be injected
     * into {@code bot}.
     *
     * @param bot The bot for which this context is being evaluated.
     * @return The contextual location, or {@code null} if this event is not injectable.
     */
    Locatable contextLocatable(Bot bot);

    /**
     * Returns the maximum distance (in tiles) from the context location at which this event remains visible or
     * relevant to the specified {@link Bot}.
     * <p>
     * The default implementation returns {@link Position#VIEWING_DISTANCE}, meaning that bots within the normal
     * viewing range will receive the event. A return value of {@code -1} indicates that this event should never be
     * viewable or injected.
     *
     * @param bot The bot for which this radius is being evaluated.
     * @return The radius of visibility, or {@code -1} if this event is never viewable.
     */
    default int contextRadius(Bot bot) {
        return Position.VIEWING_DISTANCE;
    }
}
