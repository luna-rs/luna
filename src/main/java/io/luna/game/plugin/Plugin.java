package io.luna.game.plugin;

import com.google.common.collect.ImmutableSet;

import java.io.File;
import java.nio.file.Path;
import java.util.Set;

/**
 * A model representing a single plugin. Plugins are a collection of {@link Script}s that make
 * up specific pieces of content. They adhere to a set of rules
 * <ul>
 * <li>Different plugins cannot share the same name
 * <li>Plugins cannot be placed in same directory as the API
 * <li>A plugin cannot have another plugin in itself
 * </ul>
 * Plugin instances are <strong>always</strong> immutable, and therefore safe to access across multiple
 * threads.
 *
 * @author lare96 <http://github.com/lare96>
 */
public final class Plugin {

    /**
     * The metadata describing this plugin.
     */
    private final PluginMetadata metadata;

    /**
     * The root directory of this plugin.
     */
    private final Path dir;

    /**
     * The fully qualified package name.
     */
    private final String packageName;

    /**
     * An immutable set of script dependencies.
     */
    private final ImmutableSet<ScriptDependency> dependencies;

    /**
     * An immutable set of scripts.
     */
    private final ImmutableSet<Script> scripts;

    /**
     * Creates a new {@link Plugin}.
     *
     * @param metadata The metadata describing this plugin.
     * @param pluginDir The directory of the plugin.
     * @param scripts A set of scripts.
     */
    public Plugin(PluginMetadata metadata, Path pluginDir, Set<ScriptDependency> dependencies, Set<Script> scripts) {
        this.metadata = metadata;
        this.scripts = ImmutableSet.copyOf(scripts);
        this.dependencies = ImmutableSet.copyOf(dependencies);
        dir = pluginDir;
        packageName = computePackageName(pluginDir);
    }

    /**
     * Computes the fully qualified package name.
     *
     * @return The package name.
     */
    private String computePackageName(Path dir) {
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
     * @return The metadata describing this plugin.
     */
    public PluginMetadata getMetadata() {
        return metadata;
    }

    /**
     * @return The root directory of this plugin.
     */
    public Path getDir() {
        return dir;
    }

    /**
     * @return The fully qualified package name.
     */
    public String getPackageName() {
        return packageName;
    }

    /**
     * @return An immutable set of script dependencies.
     */
    public ImmutableSet<ScriptDependency> getDependencies() {
        return dependencies;
    }

    /**
     * @return An immutable set of scripts.
     */
    public ImmutableSet<Script> getScripts() {
        return scripts;
    }
}