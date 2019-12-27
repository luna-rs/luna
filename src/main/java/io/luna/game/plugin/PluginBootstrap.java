package io.luna.game.plugin;

import com.google.common.io.MoreFiles;
import com.moandjiezana.toml.Toml;
import io.github.classgraph.ClassGraph;
import io.luna.LunaContext;
import io.luna.game.event.EventListener;
import io.luna.game.event.EventListenerPipelineSet;
import io.luna.game.event.EventMatcherListener;
import io.luna.util.AsyncExecutor;
import io.luna.util.ThreadUtils;
import io.luna.util.Tuple;
import io.luna.util.gui.PluginGui;
import kotlin.script.templates.standard.ScriptTemplateWithArgs;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
        private final Path pluginDir;

        /**
         * The plugin metadata file directory.
         */
        private final Path pluginMetadataDir;

        /**
         * Creates a new {@link PluginDirLoader}.
         *
         * @param pluginDir The plugin directory.
         * @param pluginMetadataDir The plugin metadata file directory.
         */
        public PluginDirLoader(Path pluginDir, Path pluginMetadataDir) {
            this.pluginDir = pluginDir;
            this.pluginMetadataDir = pluginMetadataDir;
        }

        @Override
        public void run() {
            //noinspection ConstantConditions
            Set<String> kotlinFiles = Arrays.stream(pluginDir.toFile().list()).
                    filter(f -> f.endsWith(".kt") || f.endsWith(".kts")).
                    collect(Collectors.toSet());

            // Metadata TOML -> Java
            var pluginMetadata = new Toml().read(pluginMetadataDir.toFile()).
                    getTable("metadata").to(PluginMetadata.class);

            // Check for duplicate plugins.
            if (plugins.containsKey(pluginMetadataDir)) {
                throw new IllegalStateException("Cannot have multiple plugins in the same directory (" + pluginDir + ").");
            }

            // Load all non-metadata plugin files.
            for (String nextFile : kotlinFiles) {
                try {
                    loadFile(nextFile);
                } catch (IOException e) {
                    logger.warn("An error occurred while loading plugin files.", e);
                    return;
                }
            }
            plugins.put(pluginMetadataDir, new Plugin(pluginMetadata, pluginDir, dependencies, scripts));
        }

        /**
         * Loads the file with {@code fileName} into one of the backing sets.
         *
         * @param fileName The file to load.
         */
        private void loadFile(String fileName) throws IOException {
            Path path = pluginDir.resolve(fileName);
            if (fileName.endsWith(".kts")) {
                // Load script file.
                String scriptContents = Files.readString(path);
                scripts.add(new Script(fileName, path));
            } else {
                // Load dependency file.
                dependencies.add(new ScriptDependency(fileName, path));
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
    private static final Logger logger = LogManager.getLogger();

    /**
     * The directory containing plugin files.
     */
    static final Path DIR = Path.of("plugins");

    /**
     * The bindings. Has to be global in order for Kotlin scripts to access it.
     */
    private static KotlinBindings bindings;

    /**
     * A map of plugin names to instances.
     */
    private final Map<Path, Plugin> plugins = new ConcurrentHashMap<>();

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
        var pluginManager = context.getPlugins();
        var gameService = context.getGame();

        initFiles();
        var pluginCountTuple = initPlugins(displayGui);

        EventListenerPipelineSet oldPipelines = pluginManager.getPipelines();
        EventListenerPipelineSet newPipelines = bindings.getPipelines();
        gameService.sync(() -> oldPipelines.replaceAll(newPipelines));
        return pluginCountTuple;
    }

    /**
     * Concurrently parses files in the plugin directory and caches their contents.
     */
    private void initFiles() {
        var threadPool = new AsyncExecutor(ThreadUtils.cpuCount(), "PluginDirInitThread");

        // Traverse all paths and sub-paths.
        Iterable<Path> directories = MoreFiles.fileTraverser().depthFirstPreOrder(DIR);
        for (Path dir : directories) {
            if (Files.isDirectory(dir)) {
                Path pluginMetadata = dir.resolve("plugin.toml");
                if (Files.exists(pluginMetadata)) {
                    // Submit file tasks.
                    threadPool.execute(new PluginDirLoader(dir, pluginMetadata));
                }
            }
        }

        // Await completion.
        try {
            threadPool.await(true);
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
    private Tuple<Integer, Integer> initPlugins(boolean displayGui) {

        // Launch the GUI, determine selected plugins.
        // TODO Fix the plugin GUI.
        int totalCount = plugins.size();
        //selectPlugins(displayGui);

        // Load all plugins.
        // TODO Only initialize script if its plugin script/metadata was loaded.
        var scriptArgs = new String[0];
        try (var scanResult = new ClassGraph().enableClassInfo().disableJarScanning().scan()) {
            for (var scriptClassMetadata : scanResult.getSubclasses("kotlin.script.templates.standard.ScriptTemplateWithArgs")) {
                var scriptClass = scriptClassMetadata.loadClass(ScriptTemplateWithArgs.class);
                try {
                    // Initialize the script here.
                    scriptClass.getConstructor(String[].class).newInstance((Object)scriptArgs);
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException("Error initializing script (" + scriptClass.getName() + ").");
                }

                // TODO Retrieve proper script data from metadata.
                for (EventListener<?> listener : bindings.getListeners()) {
                  //  listener.setScript(script);
                    bindings.getPipelines().add(listener);
                }
                for (EventMatcherListener<?> listener : bindings.getMatchers()) {
                   // listener.setScript(script);
                }
                bindings.getMatchers().clear();
                bindings.getListeners().clear();
            }
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
        var selectedPlugins = new HashSet<Path>();
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
}
