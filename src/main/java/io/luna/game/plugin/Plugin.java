package io.luna.game.plugin;

import com.google.common.collect.ImmutableSet;

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
     * The fully qualified package name.
     */
    private final String packageName;

    /**
     * A set of scripts.
     */
    private final ImmutableSet<Script> scripts;

    /**
     * Creates a new {@link Plugin}.
     *
     * @param metadata The metadata describing this plugin.
     * @param packageName The fully qualified package name.
     * @param scripts A set of scripts.
     */
    public Plugin(PluginMetadata metadata, String packageName, Set<Script> scripts) {
        this.metadata = metadata;
        this.packageName = packageName;
        this.scripts = ImmutableSet.copyOf(scripts);
    }

    /**
     * @return The metadata describing this plugin.
     */
    public PluginMetadata getMetadata() {
        return metadata;
    }

    /**
     * @return The fully qualified package name.
     */
    public String getPackageName() {
        return packageName;
    }

    /**
     * @return A set of scripts.
     */
    public ImmutableSet<Script> getScripts() {
        return scripts;
    }
}