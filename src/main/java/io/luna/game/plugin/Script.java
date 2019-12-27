package io.luna.game.plugin;

import java.nio.file.Path;
import java.util.Objects;

/**
 * A model representing a single Kotlin script file (.kts) contained within a {@link Plugin}.
 *
 * @author lare96 <http://github.com/lare96>
 */
public final class Script {

    /**
     * The name of this script.
     */
    private final String name;

    /**
     * The path to this script.
     */
    private final Path path;

    /**
     * Creates a new {@link Script}.
     *
     * @param name The name of this script.
     * @param path The path to this script.
     */
    public Script(String name, Path path) {
        this.name = name;
        this.path = path;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof Script) {
            Script other = (Script) obj;
            return name.equals(other.name) && path.equals(other.path);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, path);
    }

    /**
     * @return The name of this script.
     */
    public String getName() {
        return name;
    }

    /**
     * @return The path to this script.
     */
    public Path getPath() {
        return path;
    }
}