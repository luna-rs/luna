package io.luna.game.event;

import com.google.common.collect.Iterators;
import com.google.common.collect.UnmodifiableIterator;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Registry of {@link EventListenerPipeline}s keyed by {@link Event} type.
 * <p>
 * This is the central container used to:
 * <ul>
 *   <li>Resolve a pipeline for a given event class (creating it lazily if absent).</li>
 *   <li>Register listeners into the appropriate pipeline.</li>
 *   <li>Swap pipeline sets during hot reload.</li>
 * </ul>
 * <p>
 * <strong>Keying note:</strong> Pipelines are currently stored by {@code eventType.getSimpleName()}. This assumes
 * event simple names are unique across the codebase/plugins.
 *
 * @author lare96
 */
public final class EventListenerPipelineSet implements Iterable<EventListenerPipeline<?>> {

    /**
     * Pipelines mapped by {@link Event} simple name.
     */
    private final Map<String, EventListenerPipeline<?>> pipelines = new HashMap<>();

    /**
     * Adds a listener to the pipeline associated with its {@link EventListener#getEventType()}.
     *
     * @param listener The listener to register.
     * @param <E> The event type.
     */
    public <E extends Event> void add(EventListener<E> listener) {
        Class<E> eventType = listener.getEventType();
        if (Event.class.isAssignableFrom(eventType)) {
            EventListenerPipeline<E> pipeline = get(eventType);
            pipeline.add(listener);
        }
    }

    /**
     * Fetches (or lazily creates) the pipeline for {@code eventType}.
     *
     * @param eventType The event class.
     * @param <E> The event type.
     * @return The pipeline responsible for dispatching {@code eventType}.
     */
    @SuppressWarnings("unchecked")
    public <E extends Event> EventListenerPipeline<E> get(Class<E> eventType) {
        EventListenerPipeline<?> pipeline = pipelines.computeIfAbsent(eventType.getSimpleName(),
                key -> new EventListenerPipeline<>(eventType));
        return (EventListenerPipeline<E>) pipeline;
    }

    /**
     * Replaces all pipelines in this set with those from {@code set}.
     * <p>
     * Used for hot reloading script/plugin listeners.
     *
     * @param set The pipeline set to copy from.
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
     * @return The number of pipelines currently registered.
     */
    public int size() {
        return pipelines.size();
    }
}
