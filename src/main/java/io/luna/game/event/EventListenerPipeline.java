package io.luna.game.event;

import com.google.common.collect.Iterators;
import com.google.common.collect.UnmodifiableIterator;
import io.luna.game.plugin.ScriptExecutionException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * A pipeline-like model of listeners contained within a pipeline set. It allows for the traversal of events through
 * it, in order to be intercepted by listeners.
 *
 * @param <E> The type of event that will traverse this pipeline.
 * @author lare96 <http://github.com/lare96>
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
     * The pipeline of listeners.
     */
    private final List<EventListener<E>> listeners = new ArrayList<>();

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

    /**
     * Posts {@code msg} to this pipeline.
     *
     * @param msg The event instance to pass.
     */
    public void post(E msg) {
        try {
            msg.setPipeline(this);

            // Attempt to match the event to a listener.
            if (!matcher.match(msg)) {

                // Event was not matched, post to other listeners.
                for (EventListener<E> listener : listeners) {
                    listener.apply(msg);
                }
            }
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
        var script = e.getScript();
        if (script != null) {
            logger.warn("Failed to run a listener from script '" + script.getInfo().getName()+ "'", e);
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
        listeners.add(listener);
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
     * Returns the amount of listeners in this pipeline.
     *
     * @return The pipeline's size.
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
