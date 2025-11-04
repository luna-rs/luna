package io.luna.game.plugin;

import com.google.common.collect.ImmutableMap;
import io.luna.LunaContext;
import io.luna.game.event.Event;
import io.luna.game.event.EventListenerPipeline;
import io.luna.game.event.EventListenerPipelineSet;
import io.luna.game.model.World;

import static java.util.Objects.requireNonNull;

/**
 * A model that acts as a bridge between interpreted Kotlin code and compiled Java code.
 *
 * @author lare96
 */
public final class PluginManager {

    /**
     * A pipeline set containing interpreted Kotlin code.
     */
    private final EventListenerPipelineSet pipelines = new EventListenerPipelineSet();

    /**
     * The world instance.
     */
    private final World world;

    /**
     * A map that holds runtime information about all plugins and their scripts (package name -> Plugin).
     */
    private volatile ImmutableMap<String, Plugin> pluginMap = ImmutableMap.of();

    /**
     * Creates a new {@link PluginManager}.
     *
     * @param context The context instance.
     */
    public PluginManager(LunaContext context) {
        world = context.getWorld();
    }

    /**
     * Traverses the event across its designated pipeline.
     *
     * @param msg The event to post.
     */
    public <E extends Event> void post(E msg) {
        EventListenerPipeline<E> pipeline = (EventListenerPipeline<E>) pipelines.get(msg.getClass());
        if (pipeline == null) {
            return;
        }
        pipeline.post(msg);

        // Handle speech injectors.
     //   BotSpeechManager speechManager = world.getBotManager().getSpeechManager();
       // speechManager.handleInjectors(msg);
    // TODO context injectors
    }

    /**
     * @return A total count of all loaded plugins (the size of {@link #pluginMap}).
     */
    public int getPluginCount() {
        return pluginMap.size();
    }

    /**
     * @return A total count of all loaded scripts.
     */
    public int getScriptCount() {
        return pluginMap.values().stream().mapToInt(scripts -> scripts.getScripts().size()).sum();
    }

    /**
     * @return The backing plugin map.
     */
    public ImmutableMap<String, Plugin> getPluginMap() {
        return pluginMap;
    }

    /**
     * Sets the backing plugin map.
     */
    void setPluginMap(ImmutableMap<String, Plugin> pluginMap) {
        this.pluginMap = requireNonNull(pluginMap);
    }

    /**
     * @return A pipeline set containing interpreted Kotlin code.
     */
    public EventListenerPipelineSet getPipelines() {
        return pipelines;
    }
}
