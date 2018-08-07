package io.luna.game.plugin;

import com.google.common.io.MoreFiles;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.moandjiezana.toml.Toml;
import io.luna.LunaContext;
import io.luna.game.GameService;
import io.luna.game.event.EventListenerPipelineSet;
import io.luna.util.BlockingTaskManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;


/**
 * A bootstrapper that initializes and evaluates all {@code Scala} dependencies and plugins.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class PluginBootstrap {

    /**
     * A task that will load a single plugin directory.
     */
    private final class PluginLoader implements Runnable {

        /**
         * All plugin files and their contents.
         */
        private final LinkedHashMap<String, String> fileMap = new LinkedHashMap<>();

        /**
         * The plugin directory.
         */
        private final Path dir;

        /**
         * The plugin metadata file directory.
         */
        private final Path pluginMetadata;

        /**
         * Creates a new {@link PluginLoader}.
         *
         * @param dir The plugin directory.
         * @param pluginMetadata The plugin metadata file directory.
         */
        public PluginLoader(Path dir, Path pluginMetadata) {
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

            // Read dependencies first, this order will be respected during script execution.
            for (String dependencies : metadata.getDependencies()) {
                loadFileContents(dependencies);
                fileList.remove(dependencies);
            }

            // Read other scripts.
            fileList.forEach(this::loadFileContents);
            plugins.put(metadata.getName(), new Plugin(metadata, fileMap));
        }

        /**
         * Loads the file with {@code fileName} into the backing map.
         */
        private void loadFileContents(String fileName) {
            try {
                byte[] sourceBytes = Files.readAllBytes(dir.resolve(fileName));
                fileMap.put(fileName, new String(sourceBytes));
            } catch (IOException e) {
                throw new RuntimeException(e);
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
     * The thread pool.
     */
    private final ListeningExecutorService threadPool;

    /**
     * The script engine evaluating {@code Scala} code.
     */
    private final ScriptEngine engine;

    /**
     * Creates a new {@link PluginBootstrap}.
     *
     * @param context The context instance.
     */
    public PluginBootstrap(LunaContext context, ListeningExecutorService threadPool) {
        this.context = context;
        this.threadPool = threadPool;
        engine = createScriptEngine();
    }

    /**
     * Creates a new {@link PluginBootstrap} that will <strong>synchronously</strong> bootstrap plugins.
     *
     * @param context The context instance.
     */
    public PluginBootstrap(LunaContext context) {
        this(context, MoreExecutors.newDirectExecutorService());
    }

    /**
     * Initializes this bootstrapper, loading all of the plugins.
     */
    public int init() throws ScriptException, InterruptedException {
        PluginManager pluginManager = context.getPlugins();
        GameService service = context.getService();

        initFiles();
        initPlugins();

        service.sync(() -> pluginManager.getPipelines().swap(pipelines));
        return plugins.size();
    }

    /**
     * Concurrently parses files in the plugin directory and caches their contents.
     */
    private void initFiles() throws InterruptedException {
        BlockingTaskManager manager = new BlockingTaskManager(threadPool);

        // Traverse all paths and sub-paths.
        Iterable<Path> directories = MoreFiles.fileTraverser().depthFirstPreOrder(DIR);
        for (Path dir : directories) {
            if (Files.isDirectory(dir)) {
                Path pluginMetadata = dir.resolve("plugin.toml");
                if (Files.exists(pluginMetadata)) {
                    // Submit -- but do not execute file tasks.
                    manager.submit(new PluginLoader(dir, pluginMetadata));
                }
            }
        }
        // Run file tasks and await completion.
        manager.await();
    }

    /**
     * Injects state into the script engine and evaluates script files.
     */
    private void initPlugins() throws ScriptException {

        // Inject context state.
        engine.put("$context$", context);
        engine.put("$logger$", LOGGER);
        engine.put("$pipelines$", pipelines);

        // Run the Scala bootstrap first.
        Plugin bootstrap = plugins.remove("Bootstrap");
        loadPlugin(bootstrap);

        // Then run other plugins.
        for (Plugin other : plugins.values()) {
            loadPlugin(other);
        }
    }

    /**
     * Evaluates a single plugin directory.
     */
    private void loadPlugin(Plugin plugin) throws ScriptException {
        Map<String, String> pluginFiles = plugin.getFiles();
        for (String file : pluginFiles.values()) {
            engine.eval(file);
        }
    }

    /**
     * Creates a new {@code Scala} script engine.
     */
    private ScriptEngine createScriptEngine() {
        ClassLoader loader = ClassLoader.getSystemClassLoader();
        return new ScriptEngineManager(loader).getEngineByName("scala");
    }
}
