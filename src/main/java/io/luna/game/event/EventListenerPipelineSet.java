package io.luna.game.event;

import com.google.common.collect.Iterators;
import com.google.common.collect.UnmodifiableIterator;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * A set of pipelines mapped to their respective event traversal types.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class EventListenerPipelineSet implements Iterable<EventListenerPipeline<?>> {

    /**
     * The map of pipelines.
     */
    public final Map<Class<?>, EventListenerPipeline<?>> pipelines = new HashMap<>();

    /**
     * Adds a new event listener to a pipeline within this set.
     */
    public void add(Class<?> messageType, EventListener<?> listener) {
        EventListenerPipeline<?> pipeline = pipelines.computeIfAbsent(messageType, EventListenerPipeline::new);
        pipeline.add(listener);
    }

    /**
     * Retrieves a pipeline from this set. Will never return {@code null}.
     */
    public EventListenerPipeline<?> get(Class<?> messageType) {
        return pipelines.get(messageType);
    }

    /**
     * Swaps the backing set with {@code set}. Used for reloading plugins (aka. hot fixing, ninja fixing, etc).
     */
    public void swap(EventListenerPipelineSet set) {
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
     */
    public int size() {
        return pipelines.size();
    }
}
