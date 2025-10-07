package io.luna.game.event;

import com.google.common.collect.Iterators;
import com.google.common.collect.UnmodifiableIterator;
import io.github.classgraph.ClassInfo;
import io.luna.game.plugin.Script;
import io.luna.game.plugin.ScriptExecutionException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.ParameterizedMessage;

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
     * Immediately dispatches the given event to this pipeline’s listeners.
     *
     * @param msg The event instance.
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
            handleException(e, false);
        } finally {
            msg.setPipeline(null);
        }
    }

    /**
     * Wraps dispatch logic in {@link Runnable} tasks for deferred execution.
     *
     * @param msg The event to dispatch.
     * @return A list of dispatch tasks.
     */
    public List<Runnable> lazyPost(E msg) {
        List<Runnable> taskBuilder = new ArrayList<>();
        if (matcher.has(msg)) {
            taskBuilder.add(() -> matcher.match(msg));
            return taskBuilder;
        }
        for (EventListener<E> eventListener : listeners) {
            taskBuilder.add(() -> {
                try {
                    eventListener.apply(msg);
                } catch (ScriptExecutionException e) {
                    handleException(e, true);
                }
            });
        }
        return taskBuilder;
    }

    /**
     * Handles a thrown {@link ScriptExecutionException} from plugins.
     *
     * @param e The exception to handle.
     */
    private void handleException(ScriptExecutionException e, boolean lazy) {
        Script script = e.getScript();
        if (script != null) {
            String s = lazy ? "lazy-run" : "run";
            ClassInfo info = script.getInfo();
            logger.warn(new ParameterizedMessage("Failed to {} a listener from script '{}' in package '{}'", s,
                    info.getSimpleName(), info.getPackageName()), e);
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
