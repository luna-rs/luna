package io.luna.game.model.path;

import java.util.Deque;

public class PathResult<T> {

    private final PathResultType type;
    private final Deque<T> path;

    public PathResult(PathResultType type, Deque<T> path) {
        this.type = type;
        this.path = path;
    }

    public PathResultType getType() {
        return type;
    }

    public Deque<T> getPath() {
        return path;
    }
}
