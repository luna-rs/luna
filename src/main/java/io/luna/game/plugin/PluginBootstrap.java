package io.luna.game.plugin;

import com.google.common.collect.ImmutableSet;
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
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
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
                    filter(f -> f.endsWith(".sc")).
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

            // Read all scripts.
            try {
                fileList.forEach(this::loadFileContents);
            } catch (LoadScriptException e) {
                LOGGER.catching(Level.WARN, e);
                return;
            }
            plugins.put(pluginName, new Plugin(metadata, computePackageName(), scripts));
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
         * Loads the file with {@code fileName} into the backing map.
         *
         * @param fileName The file to load.
         */
        private void loadFileContents(String fileName) {
            try {
                Path path = dir.resolve(fileName);
                byte[] sourceBytes = Files.readAllBytes(path);
                scripts.add(new Script(fileName, path.toAbsolutePath(), new String(sourceBytes)));
            } catch (IOException e) {
                throw new LoadScriptException(fileName, e);
            }
        }
    }

    /**
     * Creates and sets the global Scala bindings. Is only set once.
     *
     * @param context The context instance.
     */
    private static void setBindings(LunaContext context) {
        if (bindings == null) {
            bindings = new ScalaBindings(context);
        }
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
     * An immutable set containing API files.
     */
    private static final ImmutableSet<String> API_FILES = ImmutableSet.of("fields.sc", "functions.sc",
            "event_interception.sc", "implicit_classes.sc", "shop_builder.sc");

    /**
     * The bindings. Has to be global in order for Ammonite scripts to access it.
     */
    private static ScalaBindings bindings;

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
        PluginManager pluginManager = context.getPlugins();
        GameService service = context.getService();

        initFiles();
        Tuple<Integer, Integer> pluginCount = initPlugins(displayGui);

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
    private Tuple<Integer, Integer> initPlugins(boolean displayGui) throws IOException {

        // Determine selected plugins and launch the GUI.
        int totalCount = plugins.size();
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

        // Load all plugins.
        ScalaInterpreter interpreter = new ScalaInterpreter.Builder().
                predef(computePredefCode()).outStream(System.out).build();
        for (Plugin other : plugins.values()) {
            loadPlugin(other, interpreter);
        }

        int selectedCount = plugins.size();
        return new Tuple<>(selectedCount, totalCount);
    }

    /**
     * Computes the code to be used as the Ammonite scripting predef.
     *
     * @return The predef code.
     * @throws IOException If any I/O errors occur.
     */
    private String computePredefCode() throws IOException {
        StringBuilder predefCode = new StringBuilder();
        for (String file : API_FILES) {
            byte[] contents = Files.readAllBytes(DIR.resolve("api").resolve(file));
            predefCode.append(new String(contents)).
                    append('\n').
                    append('\n');
        }
        return predefCode.toString();
    }

    /**
     * Evaluates a single plugin directory.
     *
     * @param plugin The plugin to load.
     * @param interpreter The Scala interpreter.
     */
    private void loadPlugin(Plugin plugin, ScalaInterpreter interpreter) {
        for (Script script : plugin.getScripts()) {

            // Evaluate the script.
            interpreter.eval(script);

            // Add all of its listeners, reflectively set the listener script name.
            for (EventListener<?> evtListener : bindings.getListeners()) {
                evtListener.setScriptName(script.getName());
                pipelines.add(evtListener.getEventType(), evtListener);
            }
            bindings.getListeners().clear();
        }
    }
}
