package io.luna.game.plugin;

import io.luna.LunaContext;
import io.luna.game.event.Event;
import io.luna.game.event.EventFunction;
import io.luna.game.event.EventPipeline;
import io.luna.game.model.mobile.Player;

import java.util.HashMap;
import java.util.Map;

/**
 * A manager for Scala plugins and their internal functions.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class PluginManager {

    /**
     * A {@link Map} containing the event types and designated pipelines.
     */
    private final Map<Class<?>, EventPipeline<?>> plugins = new HashMap<>();

    /**
     * An instance of the {@link LunaContext}.
     */
    private final LunaContext context;

    /**
     * Creates a new {@link PluginManager}.
     *
     * @param context The context for this {@code PluginManager}.
     */
    public PluginManager(LunaContext context) {
        this.context = context;
    }

    /**
     * Submits a {@link EventFunction} to this manager.
     *
     * @param eventClass The event class type.
     * @param function The {@code EventFunction} to add to the {@link EventPipeline}.
     */
    public void submit(Class<?> eventClass, EventFunction<?> function) {
        plugins.computeIfAbsent(eventClass, it -> new EventPipeline<>()).add(function);
    }

    /**
     * Attempts to traverse {@code evt} across its designated {@link EventPipeline}.
     *
     * @param evt The event to post.
     * @param player The {@link Player} to post this event for, if intended to be {@code null} use {@code post(Event)}
     * instead.
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void post(Event evt, Player player) {
        EventPipeline pipeline = plugins.get(evt.getClass());

        if (pipeline == null) {
            return;
        }
        pipeline.traverse(evt, player);
    }

    /**
     * The equivalent to {@code post(Event, Player)}, except uses {@code null} for the {@link Player} argument.
     *
     * @param evt The event to post.
     */
    public void post(Event evt) {
        post(evt, null);
    }

    /**
     * @return An instance of the {@link LunaContext}.
     */
    public LunaContext getContext() {
        return context;
    }
}
