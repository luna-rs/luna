package io.luna.game.plugin;

import io.luna.LunaContext;
import io.luna.game.model.mobile.Player;
import plugin.Plugin;
import plugin.PluginBootstrap;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

/**
 * A class that manages all of the {@link Plugin}s and their respective {@link PluginPipeline}s. Has a function to submit a
 * new {@code Plugin} and another to post events to existing {@code Plugin}s.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class PluginManager {

    /**
     * A {@link Map} containing the event types and the designated pipelines.
     */
    private final Map<Class<?>, PluginPipeline<?>> plugins = new HashMap<>();

    /**
     * The context for this {@code PluginManager}.
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
     * Submits a {@link Plugin} represented as {@code clazz} to the backing plugin map. This should only ever be called by
     * the {@link PluginBootstrap}.
     *
     * @param clazz The class to submit as a {@code Plugin}.
     * @throws Exception If the class cannot be instantiated.
     */
    public void submit(Class<?> clazz) throws Exception {
        Type typeEvent = ((ParameterizedType) clazz.getGenericSuperclass()).getActualTypeArguments()[0];
        Plugin<?> plugin = (Plugin<?>) clazz.newInstance();

        plugin.ctx_$eq(context);

        plugin.plugins_$eq(this);
        plugin.service_$eq(context.getService());
        plugin.world_$eq(context.getWorld());

        plugins.computeIfAbsent(Class.forName(typeEvent.getTypeName()), it -> new PluginPipeline<>()).add(plugin);
    }

    /**
     * Posts an event represented as {@code evt} to all {@link Plugin}s that listen for its underlying type.
     *
     * @param evt The event to post.
     * @param player The {@link Player} to post this event for, is allowed to be {@code null} if no {@code Player} instance
     * is required.
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void post(Object evt, Player player) {
        PluginPipeline pipeline = plugins.get(evt.getClass());

        if (pipeline == null) {
            return; // Discard event silently.
        }
        pipeline.traverse(evt, player);
    }
}
