package io.luna.game.event;

import java.util.function.Function;

/**
 * Functional matcher that decides if an event should be short-circuited and not passed to standard listeners.
 *
 * @param <E> The type of event being matched.
 */
public final class EventMatcher<E extends Event> {

    /**
     * Returns a matcher that never short-circuits, always returning {@code false}.
     */
    public static <E extends Event> EventMatcher<E> defaultMatcher() {
        return new EventMatcher<>(msg -> false, msg -> false);
    }

    /* 'match' and 'has' from Kotlin. */
    private final Function<E, Boolean> matchFunc;
    private final Function<E, Boolean> hasFunc;

    /**
     * Creates a new {@link EventMatcher}.
     */
    public EventMatcher(Function<E, Boolean> matchFunc, Function<E, Boolean> hasFunc) {
        this.matchFunc = matchFunc;
        this.hasFunc = hasFunc;
    }

    /**
     * Attempts to match {@code msg} to an event listener.
     *
     * @param msg The message to match.
     * @return {@code true} if the event was matched.
     */
    public boolean match(E msg) {
        return matchFunc.apply(msg);
    }

    /**
     * Determines if this matcher has a listener for event {@code msg}.
     *
     * @param msg The event.
     * @return {@code true} if at least one listener exists, {@code false} otherwise.
     */
    public boolean has(E msg) {
        return hasFunc.apply(msg);
    }
}