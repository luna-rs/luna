package io.luna.game.event;

import io.luna.game.model.mobile.Player;
import scala.Function2;
import scala.Unit;

/**
 * A POJO that serves as a wrapper for Scala's {@link Function2} to prevent repetition of the confusing and verbose type
 * declaration. We need this because type aliases are only recognized by scala.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class EventFunction<E extends Event> {

    /**
     * The wrapped event function.
     */
    private final Function2<E, Player, Unit> function;

    /**
     * Creates a new {@link EventFunction}.
     *
     * @param function The wrapped event function.
     */
    public EventFunction(Function2<E, Player, Unit> function) {
        this.function = function;
    }

    /**
     * @return The wrapped event function.
     */
    public Function2<E, Player, Unit> getFunction() {
        return function;
    }
}
