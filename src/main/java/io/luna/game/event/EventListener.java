package io.luna.game.event;

import io.luna.game.model.mobile.Player;

import java.util.function.BiConsumer;

/**
 * A wrapper for Scala plugin listeners to prevent repetition of the confusing and verbose type declaration. In the {@link
 * EventListenerPipeline} this class acts as a listener for {@link Event}s.
 *
 * @param <E> The type of {@link Event} that this listener is listening for.
 * @author lare96 <http://github.org/lare96>
 */
public final class EventListener<E extends Event> {

    /**
     * The wrapped listener function.
     */
    private final BiConsumer<E, Player> function;

    /**
     * Creates a new {@link EventListener}.
     *
     * @param function The wrapped listener function.
     */
    public EventListener(BiConsumer<E, Player> function) {
        this.function = function;
    }

    /**
     * @return The wrapped listener function.
     */
    public BiConsumer<E, Player> getFunction() {
        return function;
    }
}
