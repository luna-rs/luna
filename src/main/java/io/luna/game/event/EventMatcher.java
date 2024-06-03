package io.luna.game.event;

import java.util.function.Function;

/**
 * A model that matches an event to an event listener. Implemented by Kotlin matchers.
 *
 * @author lare96
 */
public final class EventMatcher<E extends Event> {

    /**
     * Returns an event matcher that ignores the event and returns {@code false}.
     *
     * @param <E> The event type.
     * @return The matcher.
     */
    public static <E extends Event> EventMatcher<E> defaultMatcher() {
        return new EventMatcher<>(msg -> false);
    }

    /**
     * The matcher function.
     */
    private final Function<E, Boolean> matchFunc;

    /**
     * Creates a new {@link EventMatcher}.
     *
     * @param matchFunc The matcher function.
     */
    public EventMatcher(Function<E, Boolean> matchFunc) {
        this.matchFunc = matchFunc;
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
}