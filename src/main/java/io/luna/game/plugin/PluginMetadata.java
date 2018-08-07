package io.luna.game.plugin;

/**
 * A class representing data that describes a single plugin.
 *
 * @author lare96 <http://github.com/lare96>
 */
public final class PluginMetadata {

    /**
     * The name.
     */
    private final String name;

    /**
     * The description.
     */
    private final String description;

    /**
     * If displayed on the 'Plugin manager' GUI.
     */
    private final boolean hidden;

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
     * @param hidden If displayed on the 'Plugin manager' GUI.
     * @param dependencies The scripts to run first.
     * @param authors The authors.
     */
    public PluginMetadata(String name, String description, boolean hidden, String[] dependencies, String[] authors) {
        this.name = name;
        this.description = description;
        this.hidden = hidden;
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
     * @return If displayed on the 'Plugin manager' GUI.
     */
    public boolean isHidden() {
        return hidden;
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
