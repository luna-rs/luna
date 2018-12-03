package io.luna.game.plugin;

import java.nio.file.Path;
import java.util.Objects;

/**
 * A model representing a single Kotlin script dependency file (.kt) contained within a {@link Plugin}.
 *
 * @author lare96 <http://github.com/lare96>
 */
public final class ScriptDependency {

    /**
     * The name.
     */
    private final String name;

    /**
     * The path to the file.
     */
    private final Path path;

    /**
     * Creates a new {@link ScriptDependency}.
     *
     * @param name The name.
     * @param path The path to the file.
     */
    public ScriptDependency(String name, Path path) {
        this.name = name;
        this.path = path;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof ScriptDependency) {
            ScriptDependency other = (ScriptDependency) obj;
            return name.equals(other.name) && path.equals(other.path);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, path);
    }

    /**
     * @return The name.
     */
    public String getName() {
        return name;
    }

    /**
     * @return The path to the file.
     */
    public Path getPath() {
        return path;
    }
}