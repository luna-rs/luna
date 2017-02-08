package io.luna.game.plugin;

import com.google.common.collect.FluentIterable;
import com.google.common.io.Files;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.gson.JsonObject;
import com.moandjiezana.toml.Toml;
import io.luna.LunaContext;
import io.luna.game.GameService;
import io.luna.game.event.EventListenerPipelineSet;
import io.luna.game.model.Chance;
import io.luna.util.GsonUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import scala.Console;
import scala.tools.nsc.Settings;
import scala.tools.nsc.interpreter.IMain;
import scala.tools.nsc.settings.MutableSettings.BooleanSetting;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.apache.logging.log4j.util.Unbox.box;

/**
 * A bootstrapper that initializes and evaluates all {@code Scala} dependencies and plugins.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class PluginBootstrap implements Callable<EventListenerPipelineSet> {

    /**
     * A callback that will swap old event pipelines out for newly constructed ones.
     */
    private final class PluginBootstrapCallback implements FutureCallback<EventListenerPipelineSet> {

        @Override
        public void onSuccess(EventListenerPipelineSet result) {
            PluginManager plugins = context.getPlugins();
            plugins.getPipelines().swap(result);
        }

        @Override
        public void onFailure(Throwable t) {
            LOGGER.catching(t);
        }
    }

    /**
     * A stream that intercepts output from the {@code Scala} interpreter and forwards the output to {@code System
     * .err} when there is an evaluation error.
     */
    private final class ScalaConsole extends ByteArrayOutputStream {

        @Override
        public synchronized void flush() {
            Pattern pattern = Pattern.compile("<console>:([0-9]+): error:");

            String output = toString();
            Matcher matcher = pattern.matcher(output);

            reset();

            if (matcher.find()) {
                String fileName = currentFile.get();
                String message = output.substring(output.indexOf(':', 10) + 8);
                LOGGER
                    .fatal("Error while interpreting plugin \"{}\" {}{}", fileName, System.lineSeparator(), message);
            }
        }
    }

    /**
     * The asynchronous logger.
     */
    private static final Logger LOGGER = LogManager.getLogger();

    /**
     * The directory containing plugin files.
     */
    private static final String DIR = "./plugins/";

    /**
     * The pipeline set.
     */
    private final EventListenerPipelineSet pipelines = new EventListenerPipelineSet();

    /**
     * A map of file names to their contents.
     */
    private final Map<String, String> files = new HashMap<>();

    /**
     * The current file being evaluated.
     */
    private final AtomicReference<String> currentFile = new AtomicReference<>();

    /**
     * The context instance.
     */
    private final LunaContext context;

    /**
     * The script engine evaluating {@code Scala} code.
     */
    private final ScriptEngine engine;

    /**
     * Creates a new {@link PluginBootstrap}.
     *
     * @param context The context instance.
     */
    public PluginBootstrap(LunaContext context) {
        this.context = context;
        engine = new ScriptEngineManager().getEngineByName("scala");
    }

    @Override
    public EventListenerPipelineSet call() throws Exception {
        init();
        LOGGER.info("A total of {} Scala plugin files were successfully interpreted.", box(files.size()));
        return pipelines;
    }

    /**
     * Initializes this bootstrap using the argued listening executor.
     */
    public void load(ListeningExecutorService service) {
        Futures.addCallback(service.submit(new PluginBootstrap(context)), new PluginBootstrapCallback());
    }

    /**
     * Initializes this bootstrap using the default listening executor.
     */
    public void load() {
        GameService service = context.getService();
        Futures.addCallback(service.submit(new PluginBootstrap(context)), new PluginBootstrapCallback());
    }

    /**
     * Initializes this bootstrapper, loading all of the plugins.
     */
    private void init() throws Exception {
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
     * Parses files in the plugin directory and caches their contents.
     */
    private void initFiles() throws Exception {
        FluentIterable<File> dirFiles = Files.fileTreeTraverser().preOrderTraversal(new File(DIR))
            .filter(File::isFile);

        for (File file : dirFiles) {
            files.put(file.getName(), Files.toString(file, StandardCharsets.UTF_8));
        }
    }

    /**
     * Injects state into the script engine and evaluates dependencies.
     */
    private void initDependencies() throws Exception {
        engine.put("ctx: io.luna.LunaContext", context);
        engine.put("logger: org.apache.logging.log4j.Logger", LOGGER);
        engine.put("pipelines: io.luna.game.event.EventListenerPipelineSet", pipelines);

        engine.put("CHANCE_ALWAYS: io.luna.util.Rational", Chance.ALWAYS);
        engine.put("CHANCE_VERY_COMMON: io.luna.util.Rational", Chance.VERY_COMMON);
        engine.put("CHANCE_COMMON: io.luna.util.Rational", Chance.COMMON);
        engine.put("CHANCE_UNCOMMON: io.luna.util.Rational", Chance.UNCOMMON);
        engine.put("CHANCE_VERY_UNCOMMON: io.luna.util.Rational", Chance.VERY_UNCOMMON);
        engine.put("CHANCE_RARE: io.luna.util.Rational", Chance.RARE);
        engine.put("CHANCE_VERY_RARE: io.luna.util.Rational", Chance.VERY_RARE);

        Toml toml = new Toml().read(files.remove("dependencies.toml"));
        JsonObject reader = toml.getTable("dependencies").to(JsonObject.class);
        String parentDependency = reader.get("parent_dependency").getAsString();
        String[] childDependencies = GsonUtils.getAsType(reader.get("child_dependencies"), String[].class);

        currentFile.set(parentDependency);
        engine.eval(files.remove(parentDependency));
        for (String dependency : childDependencies) {
            currentFile.set(dependency);
            engine.eval(files.remove(dependency));
        }
    }

    /**
     * Evaluates the rest of the normal plugins.
     */
    private void initPlugins() throws Exception {
        for (Entry<String, String> fileEntry : files.entrySet()) {
            currentFile.set(fileEntry.getKey());
            engine.eval(fileEntry.getValue());
        }
    }
}
