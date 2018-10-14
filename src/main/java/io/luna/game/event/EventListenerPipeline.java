package io.luna.game.event;

import com.google.common.collect.Iterators;
import com.google.common.collect.UnmodifiableIterator;
import io.luna.game.plugin.ScriptExecutionException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

/**
 * A pipeline-like model of listeners contained within a pipeline set. It allows for the traversal of events
 * through it, in order to be intercepted by listeners.
 * <p>
 * For performance, event listeners that listen for events that are instances of {@link IdBasedEvent} can be mapped
 * by their identifiers.
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
     * The map of listeners.
     */
    private final Map<Integer, EventListener<E>> listenerMap = new HashMap<>();

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
        Iterator<EventListener<E>> listIterator = listeners.iterator();
        Iterator<EventListener<E>> mapIterator = listenerMap.values().iterator();
        return Iterators.unmodifiableIterator(Iterators.concat(listIterator, mapIterator));
    }

    /**
     * Posts {@code msg} to this pipeline.
     *
     * @param msg The event instance to pass.
     */
    public void post(E msg) {
        try {
            terminated = false;
            msg.pipeline(this);

            OptionalInt mapId = msg.getMapId();
            if (mapId.isPresent()) {
                postToMap(msg, mapId.getAsInt());
            } else {
                postToList(msg);
            }
        } catch (ScriptExecutionException e) {
            terminate();
            LOGGER.error(e);
        } finally {
            msg.pipeline(null);
        }
    }

    /**
     * Posts this event to the backing map.
     *
     * @param msg The event.
     * @param msgId The event identifier.
     */
    private void postToMap(E msg, int msgId) {
        EventListener<E> listener = listenerMap.get(msgId);
        if (listener != null) {
            listener.apply(msg);
        } else {
            postToList(msg);
        }
    }

    /**
     * Posts this event to the backing list.
     *
     * @param msg The event.
     */
    private void postToList(E msg) {
        for (EventListener<E> listener : listeners) {
            if (terminated) {
                break;
            }
            listener.apply(msg);
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
        boolean isIdBased = IdBasedEvent.class.isAssignableFrom(eventType) &&
                listener.getArgs().size() > 0;
        EventListener<E> toAdd = (EventListener<E>) listener;
        if (isIdBased) {
            addToMap(toAdd);
        } else {
            addToList(toAdd);
        }
    }


    /**
     * Adds a listener to the backing map.
     *
     * @param listener The listener.
     */
    private void addToMap(EventListener<E> listener) {
        for (Object arg : listener.getArgs()) {
            listenerMap.put((Integer) arg, listener);
        }
    }

    /**
     * Adds a listener to the backing list.
     *
     * @param listener The listener.
     */
    private void addToList(EventListener<E> listener) {
        listeners.add(listener);
    }

    /**
     * Returns the amount of listeners in this pipeline.
     *
     * @return The pipeline's size.
     */
    public int size() {
        return listeners.size() + listenerMap.size();
    }

    /**
     * @return The type of the traversing event.
     */
    public Class<?> getEventType() {
        return eventType;
    }
}
