package io.luna.game.plugin;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ScanResult;
import io.luna.LunaContext;
import io.luna.game.event.EventListener;
import io.luna.game.event.EventListenerPipelineSet;
import io.luna.game.event.EventMatcherListener;
import kotlin.script.templates.standard.ScriptTemplateWithArgs;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * A bootstrapper that loads and runs all Kotlin plugins.
 *
 * @author lare96
 */
public final class PluginBootstrap {

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
     * The bindings. Has to be global in order for Kotlin scripts to access it.
     */
    private static KotlinBindings bindings;

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
     * Initializes this bootstrapper, loading all the plugins.
     *
     * @throws ReflectiveOperationException If an error occurs while instancing plugins.
     */
    public void start() throws ReflectiveOperationException {
        var pluginManager = context.getPlugins();
        var gameService = context.getGame();

        loadPlugins();

        EventListenerPipelineSet oldPipelines = pluginManager.getPipelines();
        EventListenerPipelineSet newPipelines = bindings.getPipelines();
        gameService.sync(() -> oldPipelines.replaceAll(newPipelines));
    }

    /**
     * Searches the classpath for scripts and then validates, loads, and sorts them to be held within {@link PluginManager#getPluginMap()}.
     *
     * @throws ReflectiveOperationException If an error occurs while instancing plugins.
     */
    private void loadPlugins() throws ReflectiveOperationException {
        // Search classpath for all scripts.
        try (ScanResult result = new ClassGraph().enableClassInfo().disableJarScanning().scan()) {
            Map<String, ClassInfo> infoScripts = new HashMap<>();
            ArrayListMultimap<String, ClassInfo> pluginScripts = ArrayListMultimap.create();

            // Load all runtime information about scripts.
            loadScripts(result, infoScripts, pluginScripts);

            // Ensure that all plugin scripts have an assigned info script.
            validatePlugins(infoScripts, pluginScripts);

            // Load all build scripts and generate the plugin map.
            ImmutableMap<String, Plugin> pluginMap = buildPluginMap(infoScripts, pluginScripts);
            context.getPlugins().setPluginMap(pluginMap);
        }
    }

    /**
     * Loads runtime information about all compiled scripts.
     *
     * @param result The scan result.
     * @param infoScripts The info script map.
     * @param pluginScripts The plugin script map.
     */
    private void loadScripts(ScanResult result, Map<String, ClassInfo> infoScripts, ArrayListMultimap<String, ClassInfo> pluginScripts) {
        for (ClassInfo scriptInfo : result.getSubclasses("kotlin.script.templates.standard.ScriptTemplateWithArgs")) {
            String packageName = scriptInfo.getPackageName();
            if (scriptInfo.getSimpleName().equals("Info_plugin")) {
                // Resolve an info script.
                infoScripts.put(packageName, scriptInfo);
            } else {
                // Resolve a regular plugin script.
                pluginScripts.put(packageName, scriptInfo);
            }
        }
    }

    /**
     * Ensures all plugins have a correct and valid {@code info.plugin.kts} file.
     *
     * @param infoScripts The info script map.
     * @param pluginScripts The plugin script map.
     */
    private void validatePlugins(Map<String, ClassInfo> infoScripts, ArrayListMultimap<String, ClassInfo> pluginScripts) {
        List<Entry<String, ClassInfo>> validate = new ArrayList<>(pluginScripts.entries());
        for (Entry<String, ClassInfo> entry : validate) {
            boolean foundMatch = false;
            String packageName = entry.getKey();
            ClassInfo scriptInfo = entry.getValue();

            for (String loadedPackageName : infoScripts.keySet()) {
                // Check if every script has a matching info script.
                if (packageName.startsWith(loadedPackageName)) {
                    if (!packageName.equals(loadedPackageName)) {
                        // It does, now check if it's a nested plugin.
                        if (infoScripts.containsKey(packageName)) {
                            throw new IllegalStateException("Nesting plugins is not allowed due to confusion and potentially unpredictable behaviour. Move plugin [" + packageName + "] into its own top-level directory.");
                        }

                        // It's not a nested plugin, group it with its top level plugin.
                        pluginScripts.put(loadedPackageName, scriptInfo);
                        pluginScripts.remove(packageName, scriptInfo);
                    }
                    foundMatch = true;
                }
            }
            if (!foundMatch) {
                throw new IllegalStateException("Script [" + scriptInfo.getSimpleName() + "] in package [" + packageName + "] does not have a valid info.plugin.kts file.");
            }
        }
    }

    /**
     * Organizes all the validated scripts into a map of plugins.
     *
     * @param infoScripts The info script map.
     * @param pluginScripts The plugin script map.
     * @return The plugin map.
     */
    private ImmutableMap<String, Plugin> buildPluginMap
    (Map<String, ClassInfo> infoScripts, ArrayListMultimap<String, ClassInfo> pluginScripts) throws
            ReflectiveOperationException {
        ImmutableMap.Builder<String, Plugin> pluginMap = ImmutableMap.builder();
        for (ClassInfo infoScriptClass : infoScripts.values()) {
            String packageName = infoScriptClass.getPackageName();

            // Run the info script and retrieve metadata.
            Script infoScript = runScript(packageName, infoScriptClass);
            InfoScriptData infoScriptData = bindings.getInfo().getAndSet(null);
            if (infoScriptData == null) { // No metadata found.
                throw new IllegalStateException("No InfoScriptData found for plugin located in [" + packageName + "]");
            }
            InfoScript newInfoScript = new InfoScript(context, infoScript.getPackageName(), infoScript.getInfo(), infoScript.getDefinition(), infoScriptData);

            // Run the other scripts for this plugin, add listeners, build script list.
            ImmutableList.Builder<Script> scriptListBuilder = ImmutableList.builder();
            for (ClassInfo scriptInfo : pluginScripts.get(packageName)) {

                // Add event listeners from Kotlin code to the Java event pipelines.
                Script script = runScript(packageName, scriptInfo);
                for (EventListener<?> listener : bindings.getListeners()) {
                    listener.setScript(script);
                    bindings.getPipelines().add(listener);
                }
                for (EventMatcherListener<?> listener : bindings.getMatchers()) {
                    listener.setScript(script);
                }
                bindings.getMatchers().clear();
                bindings.getListeners().clear();

                // Add to the script list.
                scriptListBuilder.add(script);
            }

            // Add to the plugin map.
            Plugin plugin = new Plugin(packageName, newInfoScript, scriptListBuilder.build());
            pluginMap.put(packageName, plugin);
        }
        return pluginMap.build();
    }

    /**
     * Runs the contents within a compiled script, and returns a {@link Script} instance.
     *
     * @param packageName The plugin package name.
     * @param scriptInfo The runtime information about the script.
     * @return The script instance.
     * @throws ReflectiveOperationException If any errors occur.
     */
    private Script runScript(String packageName, ClassInfo scriptInfo) throws ReflectiveOperationException {
        var scriptArgs = new String[0];
        Class<ScriptTemplateWithArgs> scriptClass = scriptInfo.loadClass(ScriptTemplateWithArgs.class);
        ScriptTemplateWithArgs scriptDef = scriptClass.getConstructor(String[].class).newInstance((Object) scriptArgs);
        return new Script(context, packageName, scriptInfo, scriptDef);
    }
}
