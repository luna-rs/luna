package io.luna.game.plugin;

import com.google.common.collect.FluentIterable;
import com.google.common.io.Files;
import io.luna.LunaContext;
import scala.tools.nsc.Settings;
import scala.tools.nsc.interpreter.IMain;
import scala.tools.nsc.settings.MutableSettings.BooleanSetting;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.io.File;
import java.io.FileReader;

/**
 * A bootstrapper that initializes the Scala bindings and evaluates all of the compiled plugins.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class PluginBootstrap {

    /**
     * The path to the bootstrap.
     */
    private static final String BOOTSTRAP_DIR = "./plugins/plugin/bootstrap.scala";

    /**
     * The directory that the plugins will be evaluated from.
     */
    private static final String PLUGIN_DIR = "./plugins/plugin/";

    /**
     * The {@link LunaContext} that will be used to inject state into
     */
    private final LunaContext context;

    /**
     * The {@link ScriptEngine} that will evaluate the {@code Scala} scripts.
     */
    private final ScriptEngine scala;

    /**
     * Creates a new {@link PluginBootstrap}.
     *
     * @param context The {@link LunaContext} that will be used to inject state into
     */
    public PluginBootstrap(LunaContext context) {
        this.context = context;
        scala = new ScriptEngineManager().getEngineByName("scala");
    }

    /**
     * Configures the {@code Scala} interpreter to use the {@code Java} classpath.
     *
     * @return This class instance, for chaining.
     * @throws Exception If any problems arise configuring the interpreter.
     */
    public PluginBootstrap configure() throws Exception {
        IMain interpreter = (IMain) scala;
        Settings settings = interpreter.settings();
        BooleanSetting booleanSetting = (BooleanSetting) settings.usejavacp();

        booleanSetting.value_$eq(true);
        return this;
    }

    /**
     * Injects the {@code context} instance into global scope, and evaluates {@code bootstrap.scala}.
     *
     * @return This class instance, for chaining.
     * @throws Exception If any problems arise preparing bindings.
     */
    public PluginBootstrap bindings() throws Exception {
        scala.put("ctx: io.luna.LunaContext", context);

        scala.eval(new FileReader(BOOTSTRAP_DIR));
        return this;
    }

    /**
     * Evaluates all of the compiled plugins from within {@code PLUGIN_DIR}.
     *
     * @return This class instance, for chaining.
     * @throws Exception If any problems arise while evaluating plugins.
     */
    public PluginBootstrap evaluate() throws Exception {
        File traverseDir = new File(PLUGIN_DIR);

        FluentIterable<File> traverseFiles = Files.fileTreeTraverser().preOrderTraversal(traverseDir)
            .filter(it -> !it.getName().equals("bootstrap.scala") && it.isFile());

        for (File file : traverseFiles) {
            try {
                scala.eval(new FileReader(file));
            } catch (ScriptException e) {
                throw new PluginFailureException(e);
            }
        }
        return this;
    }
}
