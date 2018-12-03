package io.luna.game.plugin;

import org.jetbrains.kotlin.cli.common.environment.UtilKt;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

/**
 * A wrapper for {@link ScriptEngine} that interprets Kotlin scripts.
 *
 * @author lare96 <http://github.com/lare96>
 */
public final class KotlinInterpreter {

    static {
        // Needed for JSR-223, https://discuss.kotlinlang.org/t/kotlin-script-engine-error/5654
        UtilKt.setIdeaIoUseFallback();
    }

    /**
     * The Kotlin interpreter.
     */
    private final ScriptEngine interpreter;

    /**
     * Creates a new {@link KotlinInterpreter}.
     */
    KotlinInterpreter() {
        ClassLoader classLoader = ClassLoader.getSystemClassLoader();
        interpreter = new ScriptEngineManager(classLoader).getEngineByExtension("kts");
    }

    /**
     * Evaluates {@code script} using the backing interpreter.
     *
     * @param script The script to evalute.
     * @throws ScriptInterpretException If the evaluation fails.
     */
    public void eval(Script script) throws ScriptInterpretException {
        try {
            interpreter.eval(script.getContents());
        } catch (Exception e) {
            throw new ScriptInterpretException(script, e);
        }
    }
}