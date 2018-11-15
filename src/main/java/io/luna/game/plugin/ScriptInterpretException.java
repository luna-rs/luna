package io.luna.game.plugin;

import io.luna.util.ReflectionUtils;

import javax.script.ScriptException;

/**
 * A {@link RuntimeException} implementation thrown when a {@link Script} can't be interpreted. This class only
 * exists because {@link ScriptException} provides an inadequate amount of details about the cause of the exception.
 *
 * @author lare96 <http://github.com/lare96>
 */
public class ScriptInterpretException extends RuntimeException {

    /**
     * The script that failed.
     */
    private final Script script;

    /**
     * The reason for failure.
     */
    private final String reason;

    /**
     * The lines of code that caused the failure.
     */
    private final String codeLines;

    /**
     * Creates a new {@link ScriptInterpretException}.
     *
     * @param script The script that failed.
     * @param cause The cause of the failure.
     */
    public ScriptInterpretException(Script script, ScriptException cause) {
        super();
        this.script = script;
        reason = computeReason(cause);
        codeLines = computeLines(cause);
    }

    @Override
    public String getMessage() {
        return "\n\t\tINTERPRET FAILED: " + script.getName() + '\n' +
                "\t\tREASON: " + reason + '\n' +
                "\t\t@ LINES: \n" + codeLines;
    }

    /**
     * Computes the reason for the interpretation failure.
     *
     * @param cause The cause of the failure.
     * @return The reason for failure.
     */
    private String computeReason(ScriptException cause) {
        ReflectionUtils.setField(cause, "fileName", null);
        return cause.getMessage();
    }

    /**
     * Computes the lines that the failure occurred on.
     *
     * @param cause The cause of the failure.
     * @return The lines of code that caused the failure.
     */
    private String computeLines(ScriptException cause) {
        String[] lines = script.getContents().split("\n"); // Temporary.

        int startLine = cause.getLineNumber() - 3;
        int finishLine = cause.getLineNumber() + 3;
        startLine = startLine < 0 ? 0 : startLine;
        finishLine = finishLine >= lines.length ? lines.length - 1 : finishLine;

        StringBuilder sb = new StringBuilder();
        for (int index = startLine; index < finishLine; index++) {
            sb.append("\t> ").append(lines[index]).append('\n');
        }
        return sb.toString();
    }

    /**
     * @return The script that failed.
     */
    public Script getScript() {
        return script;
    }

    /**
     * @return The reason for failure.
     */
    public String getReason() {
        return reason;
    }

    /**
     * @return The lines of code that caused the failure.
     */
    public String getCodeLines() {
        return codeLines;
    }
}