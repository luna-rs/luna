package io.luna.game.plugin;

import io.luna.game.event.EventListener;

/**
 * A {@link RuntimeException} implementation that is thrown when a script fails to execute one of its listeners.
 *
 * @author lare96 <http://github.com/lare96>
 */
public final class ScriptExecutionException extends RuntimeException {

    /**
     * The failed listener.
     */
    private final EventListener<?> listener;

    /**
     * Creates a new {@link ScriptExecutionException}.
     *
     * @param listener The failed listener.
     * @param cause The cause of the failure.
     */
    public ScriptExecutionException(EventListener<?> listener, Exception cause) {
        super("Listener failed to execute for script: " + listener.getScript().getName(), cause);
        this.listener = listener;
    }

    /**
     * @return The failed listener.
     */
    public EventListener<?> getListener() {
        return listener;
    }
}