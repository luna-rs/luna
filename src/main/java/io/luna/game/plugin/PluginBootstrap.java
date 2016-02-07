package io.luna.game.plugin;

import com.google.common.collect.FluentIterable;
import com.google.common.io.Files;
import io.luna.LunaContext;
import plugin.ScalaBindings;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * A bootstrapper that initializes the Scala bindings and evaluates all of the compiled plugins. {@code PLUGIN_DIR} should be
 * modified to match the build path.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class PluginBootstrap {

    /**
     * The directory that the plugins will be evaluated from.
     */
    private static final Path PLUGIN_DIR = Paths.get("target/classes/plugin/");

    /**
     * The {@link LunaContext} that will be used to inject state into {@link ScalaBindings}.
     */
    private final LunaContext context;

    /**
     * Creates a new {@link PluginBootstrap}.
     *
     * @param context The {@link LunaContext} that will be used to inject state into {@link ScalaBindings}.
     */
    public PluginBootstrap(LunaContext context) {
        this.context = context;
    }

    /**
     * Initializes the Scala bindings by forcibly injecting the {@code context} state into {@link ScalaBindings}.
     *
     * @return This class instance, for chaining.
     * @throws Exception If any problems arise while initializing Scala bindings.
     */
    public PluginBootstrap bindings() throws Exception {
        Class<?> bindings = Class.forName("plugin.ScalaBindings$");

        Field bindingsField = bindings.getField("MODULE$");
        bindingsField.setAccessible(true);

        Object bindingsInstance = bindingsField.get(null);

        Field ctx = bindings.getDeclaredField("ctx");
        Field world = bindings.getDeclaredField("world");
        Field plugins = bindings.getDeclaredField("plugins");
        Field service = bindings.getDeclaredField("service");

        ctx.setAccessible(true);
        world.setAccessible(true);
        plugins.setAccessible(true);
        service.setAccessible(true);

        ctx.set(bindingsInstance, context);
        world.set(bindingsInstance, context.getWorld());
        plugins.set(bindingsInstance, context.getPlugins());
        service.set(bindingsInstance, context.getService());
        return this;
    }

    /**
     * Evaluates all of the compiled plugins from within {@code PLUGIN_DIR}.
     *
     * @return This class instance, for chaining.
     * @throws Exception If any problems arise while evaluating plugins.
     */
    public PluginBootstrap evaluate() throws Exception {
        FluentIterable<File> fileTree = Files.fileTreeTraverser().preOrderTraversal(PLUGIN_DIR.toFile())
            .filter(it -> it.getName().endsWith("$.class")).filter(it -> !it.getName().equals("ScalaBindings$.class"));

        for (File file : fileTree) {
            String className = file.getPath().replace(PLUGIN_DIR.toString(), "plugin").replace(File.separatorChar, '.')
                .replace(".class", "");
            Class.forName(className);
        }
        return this;
    }
}
