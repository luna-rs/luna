package io.luna.game.plugin;

import java.util.Objects;

/**
 * A model representing a single Scala file contained within a {@link Plugin}.
 *
 * @author lare96 <http://github.com/lare96>
 */
public final class Script {

    /**
     * The name of this script.
     */
    private final String name;

    /**
     * The contents of this script.
     */
    private final String contents;

    /**
     * Creates a new {@link Script}.
     *
     * @param name The name of this script.
     * @param contents The contents of this script.
     */
    public Script(String name, String contents) {
        this.name = name;
        this.contents = contents;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof Script) {
           Script other = (Script) obj;
            return name.equals(other.name);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    /**
     * @return The name of this script.
     */
    public String getName() {
        return name;
    }

    /**
     * @return The contents of this script.
     */
    public String getContents() {
        return contents;
    }
}