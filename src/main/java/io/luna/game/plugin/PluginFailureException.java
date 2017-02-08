package io.luna.game.plugin;

/**
 * A {@link RuntimeException} implementation thrown when a plugin fails to execute.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class PluginFailureException extends RuntimeException {

    /**
     * Creates a new {@link PluginFailureException}.
     *
     * @param reason The reason.
     */
    public PluginFailureException(String reason) {
        super(reason);
    }

    /**
     * Creates a new {@link PluginFailureException}.
     *
     * @param cause The cause.
     */
    public PluginFailureException(Exception cause) {
        super(cause.getMessage(), cause);
    }
}
