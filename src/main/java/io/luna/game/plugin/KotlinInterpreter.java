package io.luna.game.plugin;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.ParameterizedMessage;
import org.jetbrains.kotlin.cli.common.environment.UtilKt;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

/**
 * A wrapper for {@link ScriptEngine} that interprets Kotlin scripts.
 *
 * @author lare96 <http://github.com/lare96>
 */
public final class KotlinInterpreter {

    /**
     * The asynchronous logger.
     */
    private static final Logger logger = LogManager.getLogger();

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
        var classLoader = ClassLoader.getSystemClassLoader();
        interpreter = new ScriptEngineManager(classLoader).getEngineByExtension("kts");
    }

    /**
     * Evaluates {@code script} using the backing interpreter.
     *
     * @param script The script to evaluate.
     */
    public void eval(Script script) {
        try {
            interpreter.eval(script.getContents()); // TODO Use script definitions,
        } catch (ScriptException e) {
            logger.fatal(new ParameterizedMessage("Script '{}' could not be interpreted.", script.getName()), e);
            System.exit(0);
        }
    }
}