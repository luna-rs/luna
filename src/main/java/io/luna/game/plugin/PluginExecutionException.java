package io.luna.game.plugin;

import io.luna.game.event.EventListener;

/**
 * An {@link Exception} implementation that is thrown when a plugin listener fails to execute.
 *
 * @author lare96 <http://github.com/lare96>
 */
public final class PluginExecutionException extends Exception {

    /**
     * The event listener that failed to execute.
     */
    private final EventListener<?> eventListener;

    /**
     * Creates a new {@link PluginExecutionException}.
     *
     * @param eventListener The event listener that failed to execute.
     * @param cause The cause of the failed execution.
     */
    public PluginExecutionException(EventListener<?> eventListener, Exception cause) {
        super(eventListener + " failed to execute.", cause);
        this.eventListener = eventListener;
    }

    /**
     * @return The event listener that failed to execute.
     */
    public EventListener<?> getEventListener() {
        return eventListener;
    }
}