package io.luna.game.plugin;

import com.google.common.io.MoreFiles;
import com.moandjiezana.toml.Toml;
import fj.P;
import fj.P2;
import io.luna.LunaContext;
import io.luna.game.GameService;
import io.luna.game.event.EventListener;
import io.luna.game.event.EventListenerPipelineSet;
import io.luna.util.AsyncExecutor;
import io.luna.util.ThreadUtils;
import io.luna.util.gui.PluginGui;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;


/**
 * A bootstrapper that initializes and evaluates all {@code Scala} dependencies and plugins.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class PluginBootstrap {

    /**
     * A {@link RuntimeException} implementation thrown when a script file cannot be loaded.
     */
    private final class LoadScriptException extends RuntimeException {

        /**
         * Creates a new {@link LoadScriptException}.
         *
         * @param name The script name.
         * @param e The reason for failure.
         */
        public LoadScriptException(String name, IOException e) {
            super("Failed to read script " + name + ", its parent plugin will not be loaded.", e);
        }
    }

    /**
     * A task that will load a single plugin directory.
     */
    private final class PluginDirLoader implements Runnable {

        /**
         * All scripts for the plugin.
         */
        private final Set<Script> scripts = new LinkedHashSet<>();

        /**
         * The plugin directory.
         */
        private final Path dir;

        /**
         * The plugin metadata file directory.
         */
        private final Path pluginMetadata;

        /**
         * Creates a new {@link PluginDirLoader}.
         *
         * @param dir The plugin directory.
         * @param pluginMetadata The plugin metadata file directory.
         */
        public PluginDirLoader(Path dir, Path pluginMetadata) {
            this.dir = dir;
            this.pluginMetadata = pluginMetadata;
        }

        @Override
        public void run() {
            //noinspection ConstantConditions
            Set<String> fileList = Arrays.stream(dir.toFile().list()).
                    filter(f -> f.endsWith(".scala")).
                    collect(Collectors.toSet());

            // Metadata TOML -> Java
            PluginMetadata metadata = new Toml().read(pluginMetadata.toFile()).
                    getTable("metadata").to(PluginMetadata.class);

            // Check for duplicate plugins.
            String pluginName = metadata.getName();
            if (plugins.containsKey(pluginName)) {
                LOGGER.warn("Plugin [" + pluginName + "] shares the same name as another plugin.");
                return;
            }

            try {
                // Read dependencies first, this order will be respected during script execution.
                for (String dependencies : metadata.getDependencies()) {
                    loadFileContents(dependencies);
                    fileList.remove(dependencies);
                }

                // Read other scripts.
                fileList.forEach(this::loadFileContents);
            } catch (LoadScriptException e) {
                LOGGER.catching(Level.WARN, e);
                return;
            }
            plugins.put(pluginName, new Plugin(metadata, scripts));
        }

        /**
         * Loads the file with {@code fileName} into the backing map.
         *
         * @param fileName The file to load.
         */
        private void loadFileContents(String fileName) {
            try {
                byte[] sourceBytes = Files.readAllBytes(dir.resolve(fileName));
                scripts.add(new Script(fileName, new String(sourceBytes)));
            } catch (IOException e) {
                throw new LoadScriptException(fileName, e);
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
    private static final Path DIR = Paths.get("./plugins");

    /**
     * The pipeline set.
     */
    private final EventListenerPipelineSet pipelines = new EventListenerPipelineSet();

    /**
     * A map of plugin names to instances.
     */
    private final Map<String, Plugin> plugins = new ConcurrentHashMap<>();

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
        engine = createScriptEngine();
    }

    /**
     * Initializes this bootstrapper, loading all of the plugins.
     *
     * @param displayGui If the plugin GUI should be started.
     * @return Returns a numerator and denominator indicating how many plugins out of the total
     * amount were loaded.
     * @throws IOException        If an I/O error occurs.
     */
    public P2<Integer, Integer> init(boolean displayGui) throws IOException {
        PluginManager pluginManager = context.getPlugins();
        GameService service = context.getService();

        initFiles();
        P2<Integer, Integer> pluginCount = initPlugins(displayGui);

        service.sync(() -> pluginManager.getPipelines().replaceAll(pipelines));
        return pluginCount;
    }

    /**
     * Concurrently parses files in the plugin directory and caches their contents.
     */
    private void initFiles() {
        AsyncExecutor executor = new AsyncExecutor(ThreadUtils.cpuCount(), "PluginDirInitThread");

        // Traverse all paths and sub-paths.
        Iterable<Path> directories = MoreFiles.fileTraverser().depthFirstPreOrder(DIR);
        for (Path dir : directories) {
            if (Files.isDirectory(dir)) {
                Path pluginMetadata = dir.resolve("plugin.toml");
                if (Files.exists(pluginMetadata)) {
                    // Submit file tasks.
                    executor.execute(new PluginDirLoader(dir, pluginMetadata));
                }
            }
        }

        // Await completion.
        try {
            executor.await(true);
        } catch (ExecutionException e) {
            throw new CompletionException(e);
        }
    }

    /**
     * Injects state into the script engine and evaluates script files.
     *
     * @param displayGui If the plugin GUI should be started.
     * @return Returns a numerator and denominator indicating how many plugins out of the total
     * amount were loaded.
     * @throws IOException If an I/O error occurs.
     */
    private P2<Integer, Integer> initPlugins(boolean displayGui) throws IOException {
        Plugin api = plugins.remove("Plugin API"); // API not counted as a plugin.
        int totalCount = plugins.size();

        // Determine selected plugins and launch the GUI.
        final Set<String> selectedPlugins = new HashSet<>();
        if (displayGui) {
            PluginGui gui = new PluginGui(plugins);
            selectedPlugins.addAll(gui.launch());
        } else {
            Toml guiSettings = new Toml().
                    read(new File("./data/gui/settings.toml")).
                    getTable("settings");
            boolean retainSelection = guiSettings.getBoolean("retain_selection");
            Collection<String> selected = retainSelection ?
                    guiSettings.getList("selected") : plugins.keySet();
            selectedPlugins.addAll(selected);
        }
        plugins.keySet().retainAll(selectedPlugins);

        // Inject context state.
        List<EventListener<?>> scriptListeners = new ArrayList<>();
        engine.put("$context$", context);
        engine.put("$logger$", LOGGER);
        engine.put("$scriptListeners$", scriptListeners);

        // Load the Plugin API into memory.
        loadPlugin(api, scriptListeners);

        // Then load other plugins.
        for (Plugin other : plugins.values()) {
            loadPlugin(other, scriptListeners);
        }

        int selectedCount = plugins.size();
        return P.p(selectedCount, totalCount);
    }

    /**
     * Evaluates a single plugin directory.
     *
     * @param plugin The plugin to load.
     * @param listeners A list of event listeners within a script.
     */
    private void loadPlugin(Plugin plugin, List<EventListener<?>> listeners) {
        for (Script script : plugin.getScripts()) {

            // Load the script.
            try {
                engine.eval(script.getContents());
            } catch (ScriptException e) {
                throw new ScriptInterpretException(script, e);
            }

            // Add all of its listeners, reflectively set the listener script name.
            for (EventListener<?> evtListener : listeners) {
                evtListener.setScriptName(script.getName());
                pipelines.add(evtListener.getEventType(), evtListener);
            }
            listeners.clear();
        }
    }

    /**
     * Creates a new {@code Scala} script engine.
     *
     * @return The script engine.
     */
    private ScriptEngine createScriptEngine() {
        ClassLoader loader = ClassLoader.getSystemClassLoader();
        return new ScriptEngineManager(loader).getEngineByName("scala");
    }
}
