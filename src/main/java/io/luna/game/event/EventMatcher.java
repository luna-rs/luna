package io.luna.game.event;

import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.interact.InteractionActionListener;

import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Lightweight routing layer used by {@link EventListenerPipeline} to dispatch keyed or filtered events.
 * <p>
 * This is primarily used as an optimization. Rather than iterating every listener in a pipeline, the matcher can
 * route an event directly to the relevant subset of listeners using a key-based hash lookup.
 *
 * @param <E> The event type being routed.
 * @author lare96
 */
public final class EventMatcher<E extends Event> {

    /**
     * Creates a matcher that performs no routing and never short-circuits.
     * <p>
     * {@link #match(Event)} always returns {@code false}, {@link #has(Event)} always returns
     * {@code false}, and {@link #interactions(Player, Event)} always returns an empty list.
     *
     * @param <E> The event type handled by the matcher.
     * @return A no-op matcher.
     */
    public static <E extends Event> EventMatcher<E> defaultMatcher() {
        return new EventMatcher<>(msg -> false, msg -> false, (plr, msg) -> Collections.emptyList());
    }

    /**
     * The routing function used to attempt matcher-backed dispatch.
     */
    private final Function<E, Boolean> matchFunction;

    /**
     * The function used to determine whether matcher-backed listeners exist for an event.
     */
    private final Function<E, Boolean> hasFunction;

    /**
     * The function used to create deferred interaction listeners for an event.
     */
    private final BiFunction<Player, E, List<InteractionActionListener>> interactionsFunction;

    /**
     * Creates a new {@link EventMatcher}.
     *
     * @param matchFunction The dispatch attempt function.
     * @param hasFunction The existence-check function.
     * @param interactionsFunction The interaction listener creation function.
     */
    public EventMatcher(Function<E, Boolean> matchFunction, Function<E, Boolean> hasFunction,
                        BiFunction<Player, E, List<InteractionActionListener>> interactionsFunction) {
        this.matchFunction = matchFunction;
        this.hasFunction = hasFunction;
        this.interactionsFunction = interactionsFunction;
    }

    /**
     * Attempts to route and dispatch {@code msg} to matcher-backed listeners.
     *
     * @param msg The event to route.
     * @return {@code true} if at least one listener was matched and handled, otherwise
     *         {@code false}.
     */
    public boolean match(E msg) {
        return matchFunction.apply(msg);
    }

    /**
     * Returns whether matcher-backed listeners exist for {@code msg}.
     *
     * @param msg The event to check.
     * @return {@code true} if at least one listener exists for {@code msg}, otherwise
     *         {@code false}.
     */
    public boolean has(E msg) {
        return hasFunction.apply(msg);
    }

    /**
     * Creates deferred interaction listeners for {@code msg}.
     * <p>
     * These listeners are used by the interaction system to delay execution until the player has satisfied the
     * interaction policy.
     *
     * @param player The player attempting the interaction.
     * @param msg The interaction event being converted.
     * @return The deferred interaction listeners for {@code msg}.
     */
    public List<InteractionActionListener> interactions(Player player, E msg) {
        return interactionsFunction.apply(player, msg);
    }
}