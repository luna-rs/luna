package io.luna.game.plugin;

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

/**
 * A bootstrapper that loads and runs all Kotlin plugins.
 *
 * @author lare96 <http://github.org/lare96>
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
     * Initializes this bootstrapper, loading all of the plugins.
     *
     * @return The amount of plugins that were loaded.
     * @throws ReflectiveOperationException If an error occurs while instancing plugins.
     */
    public int start() throws ReflectiveOperationException {
        var pluginManager = context.getPlugins();
        var gameService = context.getGame();
        int pluginCount = loadPlugins();

        EventListenerPipelineSet oldPipelines = pluginManager.getPipelines();
        EventListenerPipelineSet newPipelines = bindings.getPipelines();
        gameService.sync(() -> oldPipelines.replaceAll(newPipelines));
        return pluginCount;
    }

    /**
     * Searches the classpath for scripts and loads them.
     *
     * @return The amount of plugins loaded.
     * @throws ReflectiveOperationException If an error occurs while instancing plugins.
     */
    private int loadPlugins() throws ReflectiveOperationException {
        var compiledScripts = new ArrayList<ClassInfo>();
        var buildScripts = new ArrayList<ClassInfo>();

        // Search classpath for compiled scripts.
        try (ScanResult result = new ClassGraph().enableClassInfo().disableJarScanning().scan()) {
            for (ClassInfo script : result.getSubclasses("kotlin.script.templates.standard.ScriptTemplateWithArgs")) {
                if (script.getSimpleName().equals("Build_plugin")) {
                    buildScripts.add(script);
                } else {
                    compiledScripts.add(script);
                }
            }
            // TODO Link build scripts with compiled scripts
            // TODO Only initialize script if its plugin script/metadata was loaded
            // TODO Retrieve proper script data from metadata

            // Run all compiled scripts.
            var scriptArgs = new String[0];
            for (ClassInfo scriptInfo : compiledScripts) {
                Class<ScriptTemplateWithArgs> scriptClass = scriptInfo.loadClass(ScriptTemplateWithArgs.class);
                ScriptTemplateWithArgs scriptInstance = scriptClass.getConstructor(String[].class).newInstance((Object) scriptArgs);

                RuntimeScript script = new RuntimeScript(scriptInfo, scriptInstance);
                for (EventListener<?> listener : bindings.getListeners()) {
                    listener.setScript(script);
                    bindings.getPipelines().add(listener);
                }
                for (EventMatcherListener<?> listener : bindings.getMatchers()) {
                    listener.setScript(script);
                }
                bindings.getMatchers().clear();
                bindings.getListeners().clear();
            }
        }
        logger.debug("{} compiled Kotlin scripts have been initialized.", compiledScripts.size());
        return buildScripts.size();
    }
}
