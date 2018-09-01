package io.luna.game.event;

import com.google.common.collect.Iterators;
import com.google.common.collect.UnmodifiableIterator;
import io.luna.game.plugin.PluginExecutionException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * A pipeline-like model of listeners contained within a pipeline set. It allows for the traversal of events
 * through it, in order to be intercepted.
 *
 * @param <E> The type of event that will traverse this pipeline.
 * @author lare96 <http://github.org/lare96>
 */
public final class EventListenerPipeline<E extends Event> implements Iterable<EventListener<E>> {

    /**
     * The asynchronous logger.
     */
    private static final Logger LOGGER = LogManager.getLogger();

    /**
     * The type of the traversing event.
     */
    private final Class<?> eventType;

    /**
     * The pipeline of listeners.
     */
    private final List<EventListener<E>> listeners = new ArrayList<>();

    /**
     * A flag determining if a traversal terminated.
     */
    private boolean terminated;

    /**
     * Creates a new {@link EventListenerPipeline}.
     *
     * @param eventType The type of the traversing event.
     */
    public EventListenerPipeline(Class<?> eventType) {
        this.eventType = eventType;
    }

    @Override
    public UnmodifiableIterator<EventListener<E>> iterator() {
        return Iterators.unmodifiableIterator(listeners.iterator());
    }

    /**
     * Passes {@code msg} to each listener in this pipeline.
     *
     * @param msg The event instance to pass.
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
        } catch (PluginExecutionException e) {
            terminate();
            LOGGER.warn(e);
        } finally {
            msg.pipeline(null);
        }
    }

    /**
     * Terminates an active traversal of this pipeline.
     *
     * @return {@code true} if termination was successful, {@code false} if this pipeline was
     * already terminated.
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
     *
     * @param listener The listener.
     */
    @SuppressWarnings("unchecked")
    public void add(EventListener<?> listener) {
        listeners.add((EventListener<E>) listener);
    }

    /**
     * Returns the amount of listeners in this pipeline.
     *
     * @return The pipeline's size.
     */
    public int size() {
        return listeners.size();
    }

    /**
     * @return The type of the traversing event.
     */
    public Class<?> getEventType() {
        return eventType;
    }
}
