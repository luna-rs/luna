package io.luna.game.event;

import com.google.common.collect.Iterators;
import com.google.common.collect.UnmodifiableIterator;

import java.util.ArrayList;
import java.util.List;

/**
 * A pipeline-like model of listeners contained within a pipeline set. It allows for the traversal of events
 * through it, in order to be intercepted.
 *
 * @param <E> The type of events that will traverse this pipeline.
 * @author lare96 <http://github.org/lare96>
 */
public final class EventListenerPipeline<E extends Event> implements Iterable<EventListener<E>> {

    /**
     * The type of message traversing this pipeline.
     */
    private final Class<?> messageType;

    /**
     * The pipeline of listeners.
     */
    private final List<EventListener<E>> listeners = new ArrayList<>();

    /**
     * A flag determining if a traversal was terminated.
     */
    private boolean terminated;

    /**
     * Creates a new {@link EventListenerPipeline}.
     *
     * @param messageType The type of message traversing this pipeline.
     */
    public EventListenerPipeline(Class<?> messageType) {
        this.messageType = messageType;
    }

    @Override
    public UnmodifiableIterator<EventListener<E>> iterator() {
        return Iterators.unmodifiableIterator(listeners.iterator());
    }

    /**
     * Passes {@code msg} to each listener in this pipeline.
     */
    public void traverse(E msg) {
        try {
            terminated = false;
            msg.pipeline(this);

            for (EventListener<E> listener : listeners) {
                if (terminated) {
                    break;
                }
                listener.apply(msg);
            }
        } finally {
            msg.pipeline(null);
        }
    }

    /**
     * Terminates an active traversal of this pipeline.
     */
    public boolean terminate() {
        if (!terminated) {
            terminated = true;
            return true;
        }
        return false;
    }

    /**
     * Adds a new listener to this pipeline. Usually invoked through a pipeline set.
     */
    @SuppressWarnings("unchecked")
    public void add(EventListener<?> listener) throws ClassCastException {
        listeners.add((EventListener<E>) listener);
    }

    /**
     * Returns the amount of listeners in this pipeline.
     */
    public int size() {
        return listeners.size();
    }

    /**
     * @return The type of message traversing this pipeline.
     */
    public Class<?> getMessageType() {
        return messageType;
    }
}
