package io.luna.game.plugin;

import com.google.common.collect.FluentIterable;
import com.google.common.io.Files;
import com.google.gson.JsonObject;
import com.moandjiezana.toml.Toml;
import io.luna.LunaContext;
import io.luna.util.GsonUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import scala.Console;
import scala.tools.nsc.Settings;
import scala.tools.nsc.interpreter.IMain;
import scala.tools.nsc.settings.MutableSettings.BooleanSetting;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A bootstrapper that initializes and evaluates all {@code Scala} dependencies and plugins.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class PluginBootstrap implements Runnable {

    /**
     * A {@link ByteArrayOutputStream} implementation that intercepts output from the {@code Scala} interpreter and forwards
     * the output to {@code System.err} when there is an evaluation error.
     */
    private static final class ScalaConsole extends ByteArrayOutputStream {

        @Override
        public synchronized void flush() {
            Pattern pattern = Pattern.compile("<console>:([0-9]+): error:");

            String output = toString();
            Matcher matcher = pattern.matcher(output);

            reset();

            if (matcher.find()) {
                LOGGER.error(output);
            }
        }
    }

    /**
     * The asynchronous logger.
     */
    private static final Logger LOGGER = LogManager.getLogger();

    /**
     * The directory that contains all files related to plugins.
     */
    private static final String DIR = "./plugins/plugin/";

    /**
     * The {@link LunaContext} that will be used to inject state into plugins.
     */
    private final LunaContext context;

    /**
     * The {@link ScriptEngine} that will evaluate the {@code Scala} scripts.
     */
    private final ScriptEngine engine;

    /**
     * A {@link Map} of the file names in {@code DIR} to their contents.
     */
    private final Map<String, String> files = new HashMap<>();

    /**
     * Creates a new {@link PluginBootstrap}.
     *
     * @param context The {@link LunaContext} that will be used to inject state into plugins.
     */
    public PluginBootstrap(LunaContext context) {
        this.context = context;
        engine = new ScriptEngineManager().getEngineByName("scala");
    }

    @Override
    public void run() {
        try {
            init();
        } catch (Exception e) {
            throw new RuntimeException("Error initializing Scala plugins...", e);
        }
    }

    /**
     * Initializes this bootstrapper, loading all of the plugins.
     */
    public void init() throws Exception {
        PrintStream oldConsole = Console.out();
        ScalaConsole newConsole = new ScalaConsole();

        Console.setOut(new PrintStream(newConsole));
        try {
            initClasspath();
            initFiles();
            initDependencies();
            initPlugins();
        } finally {
            Console.setOut(oldConsole);
        }
    }

    /**
     * Configures the {@code Scala} interpreter to use the {@code Java} classpath.
     */
    private void initClasspath() throws Exception {
        IMain interpreter = (IMain) engine;
        Settings settings = interpreter.settings();
        BooleanSetting booleanSetting = (BooleanSetting) settings.usejavacp();

        booleanSetting.value_$eq(true);
    }

    /**
     * Parses all of the files in {@code DIR} and caches their contents into {@code files}.
     */
    private void initFiles() throws Exception {
        FluentIterable<File> dirFiles = Files.fileTreeTraverser().preOrderTraversal(new File(DIR)).filter(File::isFile);

        for (File file : dirFiles) {
            files.put(file.getName(), Files.toString(file, StandardCharsets.UTF_8));
        }
    }

    /**
     * Injects state into the {@code engine} and evaluates dependencies from {@code DIR}.
     */
    private void initDependencies() throws Exception {
        engine.put("ctx: io.luna.LunaContext", context);
        engine.put("logger: org.apache.logging.log4j.Logger", LOGGER);

        Toml toml = new Toml().read(files.remove("dependencies.toml"));

        JsonObject reader = toml.getTable("dependencies").to(JsonObject.class);
        String parentDependency = reader.get("parent_dependency").getAsString();
        String[] childDependencies = GsonUtils.getAsType(reader.get("child_dependencies"), String[].class);

        engine.eval(files.remove(parentDependency));
        for (String dependency : childDependencies) {
            engine.eval(files.remove(dependency));
        }
    }

    /**
     * Evaluates all of the dependant plugins from within {@code DIR}.
     */
    private void initPlugins() throws Exception {
        for (Entry<String, String> fileEntry : files.entrySet()) {
            try {
                engine.eval(fileEntry.getValue());
            } catch (ScriptException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
