package io.luna.game.event;

import io.luna.game.plugin.PluginFailureException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.function.Consumer;

/**
 * A listener that intercepts events.
 *
 * @param <E> The type of event being intercepted.
 * @author lare96 <http://github.org/lare96>
 */
public final class EventListener<E extends Event> {

    /**
     * The asynchronous logger.
     */
    private static final Logger LOGGER = LogManager.getLogger();

    /**
     * The listener function.
     */
    private final Consumer<E> listener;

    /**
     * Creates a new {@link EventListener}.
     *
     * @param listener The listener function.
     */
    public EventListener(Consumer<E> listener) {
        this.listener = listener;
    }

    /**
     * Applies the wrapped function and handles exceptions.
     */
    public void apply(E msg) throws PluginFailureException {
        try {
            listener.accept(msg);
        } catch (PluginFailureException failure) { // fail, recoverable
            LOGGER.catching(failure);
        } catch (Exception other) { // unknown, unrecoverable
            throw new PluginFailureException(other);
        }
    }

    /**
     * Returns the raw listener function, without error handling.
     */
    public Consumer<E> getListener() {
        return listener;
    }
}
