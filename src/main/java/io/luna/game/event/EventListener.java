package io.luna.game.event;

import com.google.common.base.MoreObjects;
import com.google.common.collect.Iterables;
import io.luna.game.plugin.PluginExecutionException;

import java.util.function.Consumer;

/**
 * A listener that intercepts events.
 *
 * @param <E> The type of event being intercepted.
 * @author lare96 <http://github.org/lare96>
 */
public final class EventListener<E extends Event> {

    /**
     * The type of event being intercepted.
     */
    private final Class<?> eventType;

    /**
     * The arguments.
     */
    private final EventArguments args;

    /**
     * The listener function.
     */
    private final Consumer<E> listener;

    /**
     * Creates a new {@link EventListener}.
     *
     * @param eventType The type of event being intercepted.
     * @param args The arguments.
     * @param listener The listener function.
     */
    public EventListener(Class<?> eventType, EventArguments args, Consumer<E> listener) {
        this.eventType = eventType;
        this.listener = listener;
        this.args = args;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).
                add("eventType", eventType.getSimpleName()).
                add("args", Iterables.toString(args)).toString();
    }

    /**
     * Applies the wrapped function and handles exceptions.
     *
     * @param msg The event to apply the function with.
     * @throws PluginExecutionException If an error occurs applying {@code msg} to the listener.
     */
    public void apply(E msg) throws PluginExecutionException {
        try {
            if (args == EventArguments.NO_ARGS) {
                listener.accept(msg);
            } else if (msg.matches(args)) {
                listener.accept(msg);
                msg.terminate();
            }
        } catch (Exception failure) {
            throw new PluginExecutionException(this, failure);
        }
    }

    /**
     * @return The type of event being intercepted.
     */
    public Class<?> getEventType() {
        return eventType;
    }

    /**
     * @return The listener function.
     */
    public Consumer<E> getListener() {
        return listener;
    }

    /**
     * @return The arguments.
     */
    public EventArguments getArgs() {
        return args;
    }
}
