package io.luna.game.plugin;

import ammonite.util.Res;

import static com.google.common.base.Preconditions.checkState;

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
     * @param result The failing result.
     */
    public ScriptInterpretException(Script script, Res<Object> result) {
        super(result.toString());
        checkState(!result.isSuccess(), "Interpret result must be failing.");
        this.script = script;
    }

    /**
     * @return The script that failed.
     */
    public Script getScript() {
        return script;
    }
}