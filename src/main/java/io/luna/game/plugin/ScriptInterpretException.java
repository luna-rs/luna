package io.luna.game.plugin;

/**
 * A {@link RuntimeException} implementation thrown when a {@link Script} fails to be interpreted.
 *
 * @author lare96 <http://github.com/lare96>
 */
public final class ScriptInterpretException extends RuntimeException {

    /**
     * The script that failed.
     */
    private final Script script;

    /**
     * Creates a new {@link ScriptInterpretException}.
     *
     * @param script The script that failed.
     */
    public ScriptInterpretException(Script script, Exception e) {
        super(e);
        this.script = script;
    }

    /**
     * @return The script that failed.
     */
    public Script getScript() {
        return script;
    }
}