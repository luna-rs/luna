package io.luna.game.plugin;

import io.luna.LunaContext;
import io.luna.game.model.mobile.Player;
import plugin.PluginEvent;
import plugin.ScalaBindings;

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
    private final Map<Class<?>, PluginPipeline<?>> plugins = new HashMap<>();

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
     * Submits a {@link PluginFunction} to this manager. This should only be done via {@link ScalaBindings}.
     *
     * @param eventClass The event class type.
     * @param function The {@code PluginFunction} to add to the {@link PluginPipeline}.
     */
    public void submit(Class<?> eventClass, PluginFunction<?> function) {
        plugins.computeIfAbsent(eventClass, it -> new PluginPipeline<>()).add(function);
    }

    /**
     * Attempts to traverse {@code evt} across its designated {@link PluginPipeline}.
     *
     * @param evt The event to post.
     * @param player The {@link Player} to post this event for, if intended to be {@code null} use {@code post(PluginEvent)}
     * instead.
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void post(PluginEvent evt, Player player) {
        PluginPipeline pipeline = plugins.get(evt.getClass());

        if (pipeline == null) {
            return;
        }
        pipeline.traverse(evt, player);
    }

    /**
     * The equivalent to {@code post(PluginEvent, Player)}, except uses {@code null} for the {@link Player} argument.
     *
     * @param evt The event to post.
     */
    public void post(PluginEvent evt) {
        post(evt, null);
    }

    /**
     * @return An instance of the {@link LunaContext}.
     */
    public LunaContext getContext() {
        return context;
    }
}
