package io.luna.game.plugin;

import com.google.common.collect.ImmutableList;

/**
 * Represents data describing the functionality and origins of a {@link Plugin}.
 *
 * @author lare96
 */
public final class InfoScriptData {

    /**
     * The name of the plugin.
     */
    private final String name;

    /**
     * The plugin's description.
     */
    private final String description;

    /**
     * The plugin's version.
     */
    private final String version;

    /**
     * The plugin's authors.
     */
    private final ImmutableList<String> authors;

    /**
     * Creates a new {@link InfoScriptData}.
     *
     * @param name The name of the plugin.
     * @param description The plugin's description.
     * @param version The plugin's version.
     * @param authors The plugin's authors.
     */
    public InfoScriptData(String name, String description, String version, ImmutableList<String> authors) {
        this.name = name;
        this.description = description;
        this.version = version;
        this.authors = authors;
    }

    /**
     * @return The name of the plugin.
     */
    public String getName() {
        return name;
    }

    /**
     * @return The plugin's description.
     */
    public String getDescription() {
        return description;
    }

    /**
     * @return The plugin's version.
     */
    public String getVersion() {
        return version;
    }

    /**
     * @return The plugin's authors.
     */
    public ImmutableList<String> getAuthors() {
        return authors;
    }
}
