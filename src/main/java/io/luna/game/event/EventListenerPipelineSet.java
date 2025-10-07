package io.luna.game.event;

import com.google.common.collect.Iterators;
import com.google.common.collect.UnmodifiableIterator;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * A container of pipelines, each responsible for dispatching a specific type of event.
 *
 * @author lare96
 */
public final class EventListenerPipelineSet implements Iterable<EventListenerPipeline<?>> {

    /**
     * The map of pipelines.
     */
    private final Map<String, EventListenerPipeline<?>> pipelines = new HashMap<>();

    /**
     * Adds a listener to the appropriate pipeline based on its event type.
     *
     * @param listener The listener to register.
     */
    public <E extends Event> void add(EventListener<E> listener) {
        Class<E> eventType = listener.getEventType();
        if (Event.class.isAssignableFrom(eventType)) {
            EventListenerPipeline<E> pipeline = get(eventType);
            pipeline.add(listener);
        }
    }

    /**
     * Fetches or creates the pipeline for the specified event type.
     *
     * @param eventType The class of the event.
     * @return The corresponding pipeline.
     */
    @SuppressWarnings("unchecked")
    public <E extends Event> EventListenerPipeline<E> get(Class<E> eventType) {
        EventListenerPipeline<?> pipeline = pipelines.computeIfAbsent(eventType.getSimpleName(),
                key -> new EventListenerPipeline<>(eventType));
        //noinspection unchecked
        return (EventListenerPipeline<E>) pipeline;
    }

    /**
     * Replaces all current pipelines with the ones in {@code set}. Used for hot reloading.
     *
     * @param set The new pipeline set.
     */
    public void replaceAll(EventListenerPipelineSet set) {
        pipelines.clear();
        pipelines.putAll(set.pipelines);
    }

    @Override
    public UnmodifiableIterator<EventListenerPipeline<?>> iterator() {
        Collection<EventListenerPipeline<?>> values = pipelines.values();
        return Iterators.unmodifiableIterator(values.iterator());
    }

    /**
     * @return The pipeline count.
     */
    public int size() {
        return pipelines.size();
    }
}
