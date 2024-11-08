package io.luna.game.plugin;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.luna.LunaContext;
import io.luna.game.event.Event;
import io.luna.game.event.EventListenerPipeline;
import io.luna.game.event.EventListenerPipelineSet;

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
     * The context instance.
     */
    private final LunaContext context;

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
        this.context = context;
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
    }

    /**
     * Lazily traverses the event across its designated pipeline. See {@link EventListenerPipeline#lazyPost(Event)} for
     * more info.
     *
     * @param msg The event to post.
     */
    public <E extends Event> ImmutableList<Runnable> lazyPost(E msg) {
        EventListenerPipeline<E> pipeline = (EventListenerPipeline<E>) pipelines.get(msg.getClass());
        if (pipeline == null) {
            return ImmutableList.of();
        }
        return pipeline.lazyPost(msg);
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
     * @return The context instance.
     */
    public LunaContext getContext() {
        return context;
    }

    /**
     * @return A pipeline set containing interpreted Kotlin code.
     */
    public EventListenerPipelineSet getPipelines() {
        return pipelines;
    }
}
