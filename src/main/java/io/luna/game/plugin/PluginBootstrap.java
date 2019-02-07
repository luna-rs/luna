package io.luna.game.plugin;

import com.google.common.io.MoreFiles;
import com.moandjiezana.toml.Toml;
import io.luna.LunaContext;
import io.luna.game.GameService;
import io.luna.game.event.EventListener;
import io.luna.game.event.EventListenerPipelineSet;
import io.luna.util.AsyncExecutor;
import io.luna.util.ThreadUtils;
import io.luna.util.Tuple;
import io.luna.util.gui.PluginGui;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

/**
 * A bootstrapper that initializes and evaluates all {@code Kotlin} plugins.
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
         * All script dependencies for the plugin.
         */
        private final Set<ScriptDependency> dependencies = new LinkedHashSet<>();

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
                    filter(f -> f.endsWith(".kt") || f.endsWith(".kts")).
                    collect(Collectors.toSet());

            // Metadata TOML -> Java
            PluginMetadata metadata = new Toml().read(pluginMetadata.toFile()).
                    getTable("metadata").to(PluginMetadata.class);

            // Check for duplicate plugins.
            String pluginName = metadata.getName();
            if (plugins.containsKey(pluginName)) {
                // TODO Track plugins by path to plugin.toml... not name lmao
                LOGGER.warn("Plugin [" + pluginName + "] shares the same name as another plugin.");
                return;
            }
            // TODO throw exception instead?

            // Load all non-metadata plugin files.
            try {
                fileList.forEach(this::loadFile);
            } catch (LoadScriptException e) {
                LOGGER.catching(Level.WARN, e);
                return;
            }
            plugins.put(pluginName, new Plugin(metadata, computePackageName(), dependencies, scripts));
        }

        /**
         * Computes the fully qualified package name.
         *
         * @return The package name.
         */
        private String computePackageName() {
            String packageDir = dir.toString().
                    replace(File.separator, ".").
                    substring(2);
            int firstIndex = packageDir.indexOf('.');
            int lastIndex = packageDir.lastIndexOf('.');
            if (firstIndex == lastIndex) {
                return "";
            }
            return packageDir.substring(firstIndex + 1, lastIndex);
        }

        /**
         * Loads the file with {@code fileName} into one of the backing sets.
         *
         * @param fileName The file to load.
         */
        private void loadFile(String fileName) {
            try {
                Path path = dir.resolve(fileName);
                if (fileName.endsWith(".kts")) {
                    // Load script file.
                    String scriptContents = new String(Files.readAllBytes(path));
                    scripts.add(new Script(fileName, path, scriptContents));
                } else {
                    // Load dependency file.
                    dependencies.add(new ScriptDependency(fileName, path));
                }
            } catch (IOException e) {
                throw new LoadScriptException(fileName, e);
            }
        }
    }

    /**
     * Creates and sets the global Kotlin bindings. Is only set once.
     *
     * @param context The context instance.
     */
    private static void setBindings(LunaContext context) {
        bindings = new KotlinBindings(context);
    }

    /**
     * The asynchronous logger.
     */
    private static final Logger LOGGER = LogManager.getLogger();

    /**
     * The directory containing plugin files.
     */
    static final Path DIR = Paths.get("./plugins");

    /**
     * The bindings. Has to be global in order for Kotlin scripts to access it.
     */
    private static KotlinBindings bindings;

    /**
     * A map of plugin names to instances.
     */
    private final Map<String, Plugin> plugins = new ConcurrentHashMap<>();

    /**
     * The context instance.
     */
    private final LunaContext context;

    /**
     * Creates a new {@link PluginBootstrap}.
     *
     * @param context The context instance.
     */
    public PluginBootstrap(LunaContext context) {
        this.context = context;
        setBindings(context);
    }

    /**
     * Initializes this bootstrapper, loading all of the plugins.
     *
     * @param displayGui If the plugin GUI should be started.
     * @return Returns a numerator and denominator indicating how many plugins out of the total
     * amount were loaded.
     * @throws IOException If an I/O error occurs.
     */
    public Tuple<Integer, Integer> init(boolean displayGui) throws IOException {
        PluginManager plugins = context.getPlugins();
        GameService service = context.getService();

        initFiles();
        Tuple<Integer, Integer> pluginCount = initPlugins(displayGui);

        EventListenerPipelineSet oldPipelines = plugins.getPipelines();
        EventListenerPipelineSet newPipelines = bindings.getPipelines();
        service.sync(() -> oldPipelines.replaceAll(newPipelines));
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
    private Tuple<Integer, Integer> initPlugins(boolean displayGui) throws IOException {

        // Launch the GUI, determine selected plugins.
        int totalCount = plugins.size();
        selectPlugins(displayGui);

        // Load all plugins.
        KotlinInterpreter interpreter = new KotlinInterpreter();
        for (Plugin other : plugins.values()) {
            loadPlugin(other, interpreter);
        }

        int selectedCount = plugins.size();
        return new Tuple<>(selectedCount, totalCount);
    }

    /**
     * Opens the plugin GUI if needed, and determines which plugins will be loaded.
     *
     * @param displayGui If the plugin GUI should be started.
     * @throws IOException If an I/O error occurs.
     */
    private void selectPlugins(boolean displayGui) throws IOException {
        final Set<String> selectedPlugins = new HashSet<>();
        if (displayGui) {
            // Displays the GUI, grabs the plugin selection from the interface.
            PluginGui gui = new PluginGui(plugins);
            selectedPlugins.addAll(gui.launch());
        } else {
            // Loads the plugin selection from the GUI settings file.
            Toml guiSettings = new Toml().
                    read(new File("./data/gui/settings.toml")).
                    getTable("settings");

            boolean retainSelection = guiSettings.getBoolean("retain_selection");
            if (retainSelection) {
                // Load only selected plugins.
                selectedPlugins.addAll(guiSettings.getList("selected"));
            } else {
                // Load all plugins!
                selectedPlugins.addAll(plugins.keySet());
            }
        }
        plugins.keySet().retainAll(selectedPlugins);
    }

    /**
     * Evaluates a single plugin directory.
     *
     * @param plugin The plugin to load.
     * @param interpreter The Kotlin interpreter.
     */
    private void loadPlugin(Plugin plugin, KotlinInterpreter interpreter) {
        for (Script script : plugin.getScripts()) {
            // Evaluate the script.
            interpreter.eval(script);

            // Add all of its listeners, reflectively set the listener script name.
            for (EventListener<?> evtListener : bindings.getListeners()) {
                evtListener.setScript(script);
                bindings.getPipelines().add(evtListener);
            }
            bindings.getListeners().clear();
        }
    }
}
