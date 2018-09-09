package io.luna.game.plugin;

import java.util.regex.Pattern;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * A class representing data that describes a single plugin.
 *
 * @author lare96 <http://github.com/lare96>
 */
public final class PluginMetadata {

    /**
     * A regex pattern that validates the version string.
     */
    private static final Pattern VERSION = Pattern.compile("(?!\\.)(\\d+(\\.\\d+)+)(?![\\d.])$");

    /**
     * The name.
     */
    private final String name;

    /**
     * The description.
     */
    private final String description;

    /**
     * The version.
     */
    private final String version;

    /**
     * The scripts to run first.
     */
    private final String[] dependencies;

    /**
     * The authors.
     */
    private final String[] authors;

    /**
     * Creates a new {@link PluginMetadata}.
     *
     * @param name The name.
     * @param description The description.
     * @param version The version.
     * @param dependencies The scripts to run first.
     * @param authors The authors.
     */
    public PluginMetadata(String name, String description, String version, String[] dependencies, String[] authors) {
        checkArgument(VERSION.matcher(version).matches(), "Invalid version for plugin ["+ name+"].");
        this.name = name;
        this.description = description;
        this.version = version;
        this.dependencies = dependencies;
        this.authors = authors;
    }

    /**
     * @return The name.
     */
    public String getName() {
        return name;
    }

    /**
     * @return The description.
     */
    public String getDescription() {
        return description;
    }

    /**
     * @return The version.
     */
    public String getVersion() {
        return version;
    }

    /**
     * @return The scripts to run first.
     */
    public String[] getDependencies() {
        return dependencies;
    }

    /**
     * @return The authors.
     */
    public String[] getAuthors() {
        return authors;
    }
}
