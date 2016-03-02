package io.luna.game.event;

import io.luna.game.model.mobile.Player;
import scala.Function2;
import scala.Unit;

/**
 * A POJO that serves as a wrapper for Scala's {@link Function2} to prevent repetition of the confusing and verbose type
 * declaration. In the {@link EventListenerPipeline} this class acts as a listener for {@link Event}s.
 *
 * @param <E> The type of {@link Event} that this listener is listening for.
 * @author lare96 <http://github.org/lare96>
 */
public final class EventListener<E extends Event> {

    /**
     * The wrapped listener function.
     */
    private final Function2<E, Player, Unit> function;

    /**
     * Creates a new {@link EventListener}.
     *
     * @param function The wrapped listener function.
     */
    public EventListener(Function2<E, Player, Unit> function) {
        this.function = function;
    }

    /**
     * @return The wrapped listener function.
     */
    public Function2<E, Player, Unit> getFunction() {
        return function;
    }
}
