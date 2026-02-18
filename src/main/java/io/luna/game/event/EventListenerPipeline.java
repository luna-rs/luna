package io.luna.game.event;

import com.google.common.collect.Iterators;
import com.google.common.collect.UnmodifiableIterator;
import io.github.classgraph.ClassInfo;
import io.luna.game.plugin.Script;
import io.luna.game.plugin.ScriptExecutionException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Dispatch pipeline for a single {@link Event} type.
 * <p>
 * A pipeline owns all listeners registered for a specific {@code eventType} and dispatches events to them in a
 * deterministic order based on {@link EventPriority}:
 * <ol>
 *   <li>{@link EventPriority#HIGH} (at most one listener per event type)</li>
 *   <li>{@link EventPriority#NORMAL} listeners</li>
 *   <li>{@link EventMatcher} (key-based/filtered match dispatch)</li>
 *   <li>{@link EventPriority#LOW} listeners</li>
 * </ol>
 *
 * @param <E> The event type handled by this pipeline.
 * @author lare96
 */
public final class EventListenerPipeline<E extends Event> implements Iterable<EventListener<E>> {

    /**
     * The logger.
     */
    private static final Logger logger = LogManager.getLogger();

    /**
     * The event type routed through this pipeline.
     */
    private final Class<E> eventType;

    /**
     * The single high-priority listener (runs first).
     * <p>
     * This slot is reserved for "default behavior" for the event type.
     */
    private EventListener<E> priorityListener;

    /**
     * Normal-priority listeners (run after {@link #priorityListener} and before matchers).
     */
    private final List<EventListener<E>> listeners = new ArrayList<>();

    /**
     * Low-priority listeners (run last).
     */
    private final List<EventListener<E>> lazyListeners = new ArrayList<>();

    /**
     * Matcher dispatch (optimization / routing layer for keyed events).
     * <p>
     * Defaults to {@link EventMatcher#defaultMatcher()}.
     */
    private EventMatcher<E> matcher;

    /**
     * Creates a new {@link EventListenerPipeline}.
     *
     * @param eventType The event type routed through this pipeline.
     */
    public EventListenerPipeline(Class<E> eventType) {
        this.eventType = eventType;
        matcher = EventMatcher.defaultMatcher();
    }

    @Override
    public UnmodifiableIterator<EventListener<E>> iterator() {
        return Iterators.unmodifiableIterator(listeners.iterator());
    }

    /**
     * Dispatches {@code msg} immediately to this pipeline.
     * <p>
     * The event is temporarily associated with this pipeline for the duration of dispatch via
     * {@link Event#setPipeline(EventListenerPipeline)}.
     *
     * @param msg The event instance to dispatch.
     */
    public void post(E msg) {
        try {
            msg.setPipeline(this);
            internalPost(msg);
        } catch (ScriptExecutionException e) {
            handleException(e);
        } finally {
            msg.setPipeline(null);
        }
    }

    /**
     * Dispatch implementation without pipeline wiring/exception boundaries.
     */
    private void internalPost(E msg) {
        // HIGH listener always runs first.
        if (priorityListener != null) {
            priorityListener.getListener().accept(msg);
        }

        // Then NORMAL listeners.
        for (EventListener<E> listener : listeners) {
            listener.apply(msg);
        }

        // Then matcher routing (key-based / filtered).
        matcher.match(msg);

        // Then LOW listeners.
        for (EventListener<E> listener : lazyListeners) {
            listener.apply(msg);
        }
    }

    /**
     * Logs a thrown {@link ScriptExecutionException} with script attribution when available.
     *
     * @param e The exception to handle.
     */
    private void handleException(ScriptExecutionException e) {
        Script script = e.getScript();
        if (script != null) {
            ClassInfo info = script.getInfo();
            logger.warn("Failed to run a listener from script '{}' in package '{}'",
                    info.getSimpleName(), info.getPackageName(), e);
        } else {
            logger.catching(e);
        }
    }

    /**
     * Adds {@code listener} to this pipeline based on its {@link EventPriority}.
     * <p>
     * Only one {@link EventPriority#HIGH} listener may exist per event type.
     *
     * @param listener The listener to add.
     */
    public void add(EventListener<E> listener) {
        switch (listener.getPriority()) {
            case LOW:
                lazyListeners.add(listener);
                break;
            case NORMAL:
                listeners.add(listener);
                break;
            case HIGH:
                if (priorityListener != null) {
                    throw new IllegalStateException("Only one high priority event listener {" +
                            listener.getEventType().getSimpleName() + "} can exist per event type!");
                }
                priorityListener = listener;
                break;
        }
    }

    /**
     * Replaces the current matcher dispatch implementation.
     *
     * @param newMatcher The new matcher to use.
     */
    public void setMatcher(EventMatcher<E> newMatcher) {
        matcher = newMatcher;
    }

    /**
     * Returns the number of listeners in this pipeline.
     *
     * @return The number of listeners.
     */
    public int size() {
        int size = listeners.size() + lazyListeners.size() + matcher.getSize();
        if(priorityListener != null) {
            size++;
        }
        return size;
    }

    /**
     * @return The event type routed through this pipeline.
     */
    public Class<E> getEventType() {
        return eventType;
    }
}
