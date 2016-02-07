package io.luna.game.plugin;

import io.luna.game.model.mobile.Player;
import plugin.PluginEvent;
import scala.Function2;
import scala.Unit;

/**
 * A POJO wrapper for Scala's {@link Function2} to prevent repetition of the confusing and verbose type declaration. We need
 * this because type aliases are only recognized by scalac and not javac.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class PluginFunction<E extends PluginEvent> {

    /**
     * The wrapped plugin function.
     */
    private final Function2<E, Player, Unit> function;

    /**
     * Creates a new {@link PluginFunction}.
     *
     * @param function The wrapped plugin function.
     */
    public PluginFunction(Function2<E, Player, Unit> function) {
        this.function = function;
    }

    /**
     * @return The wrapped plugin function.
     */
    public Function2<E, Player, Unit> getFunction() {
        return function;
    }
}
