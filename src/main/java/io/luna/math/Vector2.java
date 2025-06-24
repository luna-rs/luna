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
     * Converts a vector into a unit vector. Unit vectors, by definition, have a magnitude of 1.
     */
    public Vector2 normalize() {
        double magnitude = Math.sqrt((this.x * this.x) + (this.y * this.y));
        if (magnitude == 0) { // We cannot divide by 0
            return new Vector2(0, 0);
        }

        // Since our game world is a grid (and therefore integer-based),
        // we round to the closest integer
        int xNorm = (int) Math.round(this.x / magnitude);
        int yNorm = (int) Math.round(this.y / magnitude);
        return new Vector2(xNorm, yNorm);
    }

    /**
     * Returns the directional distance from this vector to another vector.
     */
    public Vector2 distanceTo(Vector2 other) {
        return new Vector2(other.x - this.x, other.y - this.y);
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
