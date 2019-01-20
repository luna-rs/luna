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
     * The contents of this script.
     */
    private final String contents;

    /**
     * Creates a new {@link Script}.
     *
     * @param name The name of this script.
     * @param path The path to this script.
     * @param contents The contents of this script.
     */
    public Script(String name, Path path, String contents) {
        this.name = name;
        this.path = path;
        this.contents = contents;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Script)) {
            return false;
        }
        
        var other = (Script) obj;
    
        return name.equals(other.name) && path.equals(other.path);
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

    /**
     * @return The contents of this script.
     */
    public String getContents() {
        return contents;
    }
}