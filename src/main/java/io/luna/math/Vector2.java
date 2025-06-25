package io.luna.math;

import com.google.common.base.MoreObjects;
import io.luna.game.model.Position;

import java.util.Objects;

/**
 * A vector with 2 axis (x,y).
 *
 * @author lare96
 * @author notjuanortiz
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

    public Vector2 add(Vector2 other) {
        return new Vector2(this.x + other.x, this.y + other.y);
    }

    public Vector2 subtract(Vector2 other) {
        return new Vector2(this.x - other.x, this.y - other.y);
    }

    public Vector2 max(Vector2 other) {
        return new Vector2(Math.max(this.x, other.x), Math.max(this.y, other.y));
    }

    public Vector2 min(Vector2 other) {
        return new Vector2(Math.min(this.x, other.x), Math.min(this.y, other.y));
    }

    /**
     * Converts this vector into a unit vector with axes values of -1, 0, or 1.
     * <p>
     * This normalization function is optimized for a grid-based system.
     * </p>
     */
    public Vector2 normalize() {
        return new Vector2(Integer.signum(this.x), Integer.signum(this.y));
    }

    /**
     * Returns a unit vector representing the direction from this vector to another vector.
     */
    public Vector2 directionTowards(Vector2 other) {
        return other.subtract(this).normalize();
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
