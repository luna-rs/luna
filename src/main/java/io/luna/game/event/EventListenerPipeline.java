package io.luna.game.event;

import com.google.common.collect.Iterators;
import com.google.common.collect.UnmodifiableIterator;
import io.luna.game.model.mobile.Player;
import io.luna.game.plugin.PluginFailureException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * A pipeline-like model that allows for an {@link Event} of a specific type to be passed through it to be intercepted by
 * each individual {@link EventListener}. The traversal of the {@code Event} can be terminated at any time by invoking {@code
 * terminate()}.
 * <p>
 * Every active pipeline is contained within an {@link EventListenerPipelineSet}.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class EventListenerPipeline<E extends Event> implements Iterable<EventListener<E>> {

    /**
     * The asynchronous logger.
     */
    private static final Logger LOGGER = LogManager.getLogger();

    /**
     * A {@link List} of {@link EventListener} contained within this pipeline.
     */
    private final List<EventListener<E>> listeners = new ArrayList<>();

    /**
     * A flag that determines if a traversal has been terminated by a {@link EventListener}.
     */
    private boolean terminated;

    @Override
    public UnmodifiableIterator<EventListener<E>> iterator() {
        return Iterators.unmodifiableIterator(listeners.iterator());
    }

    /**
     * Traverse the pipeline passing the {@code evt} instance to each {@link EventListener}. A full traversal over all {@code
     * EventListener}s is not always made.
     *
     * @param evt The event to pass to each {@code EventListener}.
     * @param player The {@link Player} to pass to each {@code EventListener}, possibly {@code null}.
     */
    public void traverse(E evt, Player player) {
        try {
            terminated = false;

            evt.setPipeline(this);
            for (EventListener<E> listener : listeners) {
                if (terminated) {
                    break;
                }
                try {
                    listener.getFunction().apply(evt, player);
                } catch (PluginFailureException failure) { // fail, recoverable
                    LOGGER.catching(failure);
                } catch (Exception other) { // unknown, unrecoverable
                    throw new PluginFailureException(other);
                }
            }
        } finally {
            evt.setPipeline(null);
        }
    }

    /**
     * Terminates an active traversal of this pipeline, if this pipeline is not currently being traversed then this method
     * does nothing.
     *
     * @return {@code true} if termination was successful, {@code false} if this pipeline traversal has already been
     * terminated.
     */
    public boolean terminate() {
        if (!terminated) {
            terminated = true;
            return true;
        }
        return false;
    }

    /**
     * Adds {@code listener} to the underlying pipeline. May throw a {@link ClassCastException} if the event type doesn't
     * match the other {@link EventListener}s in this pipeline.
     *
     * @param listener The {@code PluginFunction} to add.
     */
    @SuppressWarnings("unchecked")
    public void add(EventListener<?> listener) {
        listeners.add((EventListener<E>) listener);
    }

    /**
     * @return The amount of listeners within this pipeline.
     */
    public int listenerCount() {
        return listeners.size();
    }
}
