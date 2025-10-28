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
 * A dispatch pipeline that handles a specific type of event and routes it to appropriate listeners.
 *
 * @param <E> The type of event this pipeline handles.
 * @author lare96
 */
public final class EventListenerPipeline<E extends Event> implements Iterable<EventListener<E>> {

    /**
     * The asynchronous logger.
     */
    private static final Logger logger = LogManager.getLogger();

    /**
     * The type of event that traverses this pipeline.
     */
    private final Class<E> eventType;

    /**
     * The priority listener with a priority of {@link EventPriority#HIGH}. Will always be run first.
     */
    private EventListener<E> priorityListener;

    /**
     * The regular listeners with a priority of {@link EventPriority#NORMAL}. Will be run before the matchers and
     * after the {@link #priorityListener}.
     */
    private final List<EventListener<E>> listeners = new ArrayList<>();

    /**
     * The lazy listeners with a priority of {@link EventPriority#LOW}. Will always be run last.
     */
    private final List<EventListener<E>> lazyListeners = new ArrayList<>();

    /**
     * The Kotlin match listener. Serves as an optimization for key-based events.
     */
    private EventMatcher<E> matcher;

    /**
     * Creates a new {@link EventListenerPipeline}.
     *
     * @param eventType The type of the traversing event.
     */
    public EventListenerPipeline(Class<E> eventType) {
        this.eventType = eventType;
        matcher = EventMatcher.defaultMatcher();
    }

    @Override
    public UnmodifiableIterator<EventListener<E>> iterator() {
        return Iterators.unmodifiableIterator(listeners.iterator());
    }

    private void internalPost(E msg) {
        // Priority listener always runs first.
        if (priorityListener != null) {
            priorityListener.getListener().accept(msg);
        }

        // Then NORMAL listeners.
        for (EventListener<E> listener : listeners) {
            listener.apply(msg);
        }

        // Then matchers.
        matcher.match(msg);

        // Then lazy listeners.
        for (EventListener<E> listener : lazyListeners) {
            listener.apply(msg);
        }
    }

    /**
     * Immediately dispatches the given event to this pipeline’s listeners.
     *
     * @param msg The event instance.
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
     * Handles a thrown {@link ScriptExecutionException} from plugins.
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
     * Adds a new listener to this pipeline. Usually invoked through a pipeline set.
     *
     * @param listener The listener.
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
     * Sets the match listener.
     *
     * @param newMatcher The new match listener.
     */
    public void setMatcher(EventMatcher<E> newMatcher) {
        matcher = newMatcher;
    }

    /**
     * @return The size of the pipeline.
     */
    public int size() {
        return listeners.size();
    }

    /**
     * @return The type of event that traverses this pipeline.
     */
    public Class<E> getEventType() {
        return eventType;
    }
}
