package io.luna.math;

import com.google.common.base.MoreObjects;
import io.luna.game.model.Position;
import io.luna.game.model.mob.WalkingQueue;

import java.util.Objects;

/**
 * A model representing a step in the walking queue.
 */
public final class Vector2 {

    /**
     * The x coordinate.
     */
    private final int x;

    /**
     * The y coordinate.
     */
    private final int y;

    /**
     * Creates a new {@link Vector2}.
     *
     * @param x The x coordinate.
     * @param y The y coordinate.
     */
    public Vector2(int x, int y) {
        this.x = x;
        this.y = y;
    }

    /**
     * Creates a new {@link Vector2}.
     *
     * @param position The position.
     */
    public Vector2(Position position) {
        this(position.getX(), position.getY());
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof Vector2) {
            Vector2 other = (Vector2) obj;
            return x == other.x && y == other.y;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("x", x)
                .add("y", y)
                .toString();
    }

    /**
     * @return The x coordinate.
     */
    public int getX() {
        return x;
    }

    /**
     * @return The y coordinate.
     */
    public int getY() {
        return y;
    }
}
