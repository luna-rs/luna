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
    private int x;

    /**
     * The y coordinate.
     */
    private int y;

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
        x += other.x;
        y += other.y;
        return this;
    }

    public Vector2 subtract(Vector2 other) {
        x -= other.x;
        y -= other.y;
        return this;
    }

    /**
     * Returns a new vector containing the maximum components of this vector and another vector.
     *
     * @param other The other vector to compare against.
     * @return A new vector where each component is the maximum of the corresponding components.
     */
    public Vector2 max(Vector2 other) {
        x = Math.max(x, other.x);
        y = Math.max(y, other.y);
        return this;
    }

    /**
     * Returns a new vector containing the minimum components of this vector and another vector.
     *
     * @param other The other vector to compare against.
     * @return A new vector where each component is the minimum of the corresponding components.
     */
    public Vector2 min(Vector2 other) {
        x = Math.min(x, other.x);
        y = Math.min(y, other.y);
        return this;
    }

    /**
     * Converts this vector into a unit vector with component values of -1, 0, or 1.
     * <p>
     * This normalization function is optimized for a grid-based system.
     * </p>
     */
    public Vector2 normalize() {
        x = Integer.signum(x);
        y = Integer.signum(y);
        return this;
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
