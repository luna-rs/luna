package io.luna.game.plugin;

import io.luna.LunaContext;
import io.luna.game.event.Event;
import io.luna.game.event.EventListenerPipeline;
import io.luna.game.event.EventListenerPipelineSet;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A model that acts as a bridge between interpreted Scala code and compiled Java code.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class PluginManager {

    /**
     * A pipeline set containing interpreted Scala code.
     */
    private final EventListenerPipelineSet pipelines = new EventListenerPipelineSet();

    /**
     * The context instance.
     */
    private final LunaContext context;

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
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void post(Event msg) {
        EventListenerPipeline pipeline = pipelines.get(msg.getClass());
        if (pipeline == null) {
            return;
        }
        pipeline.traverse(msg);
    }

    /**
     * @return The context instance.
     */
    public LunaContext getContext() {
        return context;
    }

    /**
     * @return A pipeline set containing interpreted Scala code.
     */
    public EventListenerPipelineSet getPipelines() {
        return pipelines;
    }
}
