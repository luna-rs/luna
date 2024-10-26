package io.luna.game.plugin;

/**
 * A {@link RuntimeException} implementation that is thrown when a script fails to execute one of its listeners.
 *
 * @author lare96
 */
public final class ScriptExecutionException extends RuntimeException {

    /**
     * The failed script.
     */
    private final Script script;

    /**
     * Creates a new {@link ScriptExecutionException}.
     *
     * @param script The failed script.
     * @param cause The cause of the failure.
     */
    public ScriptExecutionException(Script script, Exception cause) {
        super(cause);
        this.script = script;
    }

    /**
     * @return The failed script.
     */
    public Script getScript() {
        return script;
    }
}