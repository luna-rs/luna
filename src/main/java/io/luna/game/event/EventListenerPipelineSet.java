package io.luna.game.event;

import com.google.common.collect.Iterators;
import com.google.common.collect.UnmodifiableIterator;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * A model containing mappings of {@link EventListenerPipeline}s that listen for specific event types.
 * <p>
 * The {@code replacePipelines(EventListenerPipelineSet)} method allows for a completely new pipeline set to replace the
 * existing pipeline set during runtime. This allows for 'hotfixing', a process in which all {@link EventListener}s are
 * dynamically updated effectively 'refreshing' all event-based content (plugins) without compilation or restarting the
 * server.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class EventListenerPipelineSet implements Iterable<EventListenerPipeline<?>> {

    /**
     * A {@link Map} that holds a set of {@link EventListenerPipeline}s.
     */
    public final Map<Class<?>, EventListenerPipeline<?>> pipelines = new HashMap<>();

    /**
     * Add a {@link EventListener} to an {@link EventListenerPipeline}. If no pipeline exists for the event, a new one will
     * be created for it.
     *
     * @param eventClass The event listener type.
     * @param eventListener The event listener that will be added to the pipeline.
     */
    public void addEventListener(Class<?> eventClass, EventListener<?> eventListener) {
        pipelines.computeIfAbsent(eventClass, it -> new EventListenerPipeline<>()).add(eventListener);
    }

    /**
     * Retrieves the {@link EventListenerPipeline} that listens for {@code eventClass}.
     *
     * @param eventClass The type of pipeline to retrieve.
     * @return The pipeline, possibly {@code null} if no pipeline could be found.
     */
    public EventListenerPipeline<?> retrievePipeline(Class<?> eventClass) {
        return pipelines.get(eventClass);
    }

    /**
     * Dynamically replaces the backing pipeline mappings with {@code newPipelines}. The argued pipelines are not modified,
     * but references are held to its backing mappings.
     */
    public void replacePipelines(EventListenerPipelineSet newPipelines) {
        pipelines.clear();
        pipelines.putAll(newPipelines.pipelines);
    }

    @Override
    public UnmodifiableIterator<EventListenerPipeline<?>> iterator() {
        Collection<EventListenerPipeline<?>> values = pipelines.values();
        return Iterators.unmodifiableIterator(values.iterator());
    }

    /**
     * @return The amount of pipelines in this set.
     */
    public int pipelineCount() {
        return pipelines.size();
    }

    /**
     * @return The amount of listeners within every pipeline in this set.
     */
    public int listenerCount() {
        return pipelines.values().
            stream().
            mapToInt(EventListenerPipeline::listenerCount).
            sum();
    }
}
