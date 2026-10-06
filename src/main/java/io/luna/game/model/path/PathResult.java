package io.luna.game.model.path;

import java.util.Deque;

/**
 * The outcome of a path search, consisting of a {@link PathResultType} describing how the search ended and the
 * steps that make up the path.
 *
 * @param <T> The type of each step in the path.
 */
public class PathResult<T> {

    /**
     * How the path search ended.
     */
    private final PathResultType type;

    /**
     * The steps of the path, in the order they should be walked.
     */
    private final Deque<T> path;

    /**
     * Creates a new {@link PathResult}.
     *
     * @param type How the path search ended.
     * @param path The steps of the path, in the order they should be walked.
     */
    public PathResult(PathResultType type, Deque<T> path) {
        this.type = type;
        this.path = path;
    }

    /**
     * @return How the path search ended.
     */
    public PathResultType getType() {
        return type;
    }

    /**
     * @return The steps of the path, in the order they should be walked.
     */
    public Deque<T> getPath() {
        return path;
    }
}
