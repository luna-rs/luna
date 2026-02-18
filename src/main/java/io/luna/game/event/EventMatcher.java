package io.luna.game.event;

import java.util.function.Function;

/**
 * Lightweight routing layer used by {@link EventListenerPipeline} to dispatch keyed/filtered events.
 * <p>
 * This is primarily used as an optimization: rather than iterating all listeners, the matcher can route an event
 * directly to the subset of listeners using a key and hash table.
 * <p>
 * The matcher is represented by two functions:
 * <ul>
 *   <li>{@code match}: attempts dispatch and returns whether a match occurred</li>
 *   <li>{@code has}: checks if any listener exists for this event (without dispatch)</li>
 * </ul>
 *
 * @param <E> The event type being routed.
 */
public final class EventMatcher<E extends Event> {

    /**
     * Creates a matcher that performs no routing and never short-circuits.
     * <p>
     * {@link #match(Event)} always returns {@code false} and {@link #has(Event)} always returns {@code false}.
     */
    public static <E extends Event> EventMatcher<E> defaultMatcher() {
        return new EventMatcher<>(msg -> false, msg -> false, 0);
    }

    /**
     * Routing function (dispatch attempt).
     */
    private final Function<E, Boolean> matchFunction;

    /**
     * Existence check function.
     */
    private final Function<E, Boolean> hasFunction;

    /**
     * Total number of matcher listeners registered behind this matcher.
     */
    private final int size;

    /**
     * Creates a new {@link EventMatcher}.
     *
     * @param matchFunction Dispatch attempt function.
     * @param hasFunction Existence check function.
     * @param size Number of matcher listeners registered.
     */
    public EventMatcher(Function<E, Boolean> matchFunction, Function<E, Boolean> hasFunction, int size) {
        this.matchFunction = matchFunction;
        this.hasFunction = hasFunction;
        this.size = size;
    }

    /**
     * Attempts to route/dispatch {@code msg} to matcher-backed listeners.
     *
     * @param msg The event.
     * @return {@code true} if at least one listener was matched/handled.
     */
    public boolean match(E msg) {
        return matchFunction.apply(msg);
    }

    /**
     * Checks whether matcher-backed listeners exist for {@code msg}.
     *
     * @param msg The event.
     * @return {@code true} if at least one listener exists, otherwise {@code false}.
     */
    public boolean has(E msg) {
        return hasFunction.apply(msg);
    }

    /**
     * @return The number of matcher listeners registered behind this matcher.
     */
    public int getSize() {
        return size;
    }
}
