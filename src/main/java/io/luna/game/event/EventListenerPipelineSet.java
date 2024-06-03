package io.luna.game.event;

import com.google.common.collect.Iterators;
import com.google.common.collect.UnmodifiableIterator;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * A set of pipelines mapped to their respective event traversal types.
 *
 * @author lare96
 */
public final class EventListenerPipelineSet implements Iterable<EventListenerPipeline<?>> {

    /**
     * The map of pipelines.
     */
    private final Map<String, EventListenerPipeline<?>> pipelines = new HashMap<>();

    /**
     * Adds a new event listener to a pipeline within this set.
     *
     * @param listener The listener to add.
     */
    public <E extends Event> void add(EventListener<E> listener) {
        Class<E> eventType = listener.getEventType();
        if (Event.class.isAssignableFrom(eventType)) {
            EventListenerPipeline<E> pipeline = get(eventType);
            pipeline.add(listener);
        }
    }

    /**
     * Retrieves a pipeline from this set.
     *
     * @param eventType The event class to retrieve the pipeline of.
     * @return The pipeline that accepts {@code eventType}.
     */
    @SuppressWarnings("unchecked")
    public <E extends Event> EventListenerPipeline<E> get(Class<E> eventType) {
        EventListenerPipeline<?> pipeline = pipelines.computeIfAbsent(eventType.getSimpleName(),
                key -> new EventListenerPipeline<>(eventType));
        //noinspection unchecked
        return (EventListenerPipeline<E>) pipeline;
    }

    /**
     * Replaces all of the pipelines the backing map with {@code set}. Used for reloading plugins.
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
     * Returns the amount of pipelines in this set.
     *
     * @return The pipeline count.
     */
    public int size() {
        return pipelines.size();
    }
}
