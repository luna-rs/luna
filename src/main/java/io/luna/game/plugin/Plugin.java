package io.luna.game.plugin;

import com.google.common.collect.ImmutableMap;

import java.util.Map;

/**
 * A model representing a single plugin. Plugins are a collection of Scala scripts and metadata that make
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
     * A map of script names to their contents.
     */
    private final ImmutableMap<String, String> files;

    /**
     * Creates a new {@link Plugin}.
     *
     * @param metadata The metadata describing this plugin.
     * @param files A map of script names to their contents.
     */
    public Plugin(PluginMetadata metadata, Map<String, String> files) {
        this.metadata = metadata;
        this.files = ImmutableMap.copyOf(files);
    }

    /**
     * @return The metadata describing this plugin.
     */
    public PluginMetadata getMetadata() {
        return metadata;
    }

    /**
     * @return A map of script names to their contents.
     */
    public ImmutableMap<String, String> getFiles() {
        return files;
    }
}