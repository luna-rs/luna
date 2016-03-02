package io.luna.game.model;

import com.google.common.base.MoreObjects;

import java.util.Objects;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * A single absolute point on the Runescape map.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class Position {

    /**
     * The absolute {@code X} coordinate.
     */
    private final int x;

    /**
     * The absolute {@code Y} coordinate.
     */
    private final int y;

    /**
     * The absolute {@code Z} coordinate.
     */
    private final int z;

    /**
     * Creates a new {@link Position}.
     *
     * @param x The absolute {@code X} coordinate.
     * @param y The absolute {@code Y} coordinate.
     * @param z The absolute {@code Z} coordinate.
     */
    public Position(int x, int y, int z) {
        checkArgument(x >= 0, "x < 0");
        checkArgument(y >= 0, "y < 0");
        checkArgument(z >= 0 && z <= 3, "z < 0 || z > 3");

        this.x = x;
        this.y = y;
        this.z = z;
    }

    /**
     * Creates a new {@link Position} with a {@code Z} level of {@code 0}.
     *
     * @param x The absolute {@code X} coordinate.
     * @param y The absolute {@code Y} coordinate.
     */
    public Position(int x, int y) {
        this(x, y, 0);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).add("x", x).add("y", y).add("z", z).toString();
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y, z);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof Position) {
            Position other = (Position) obj;
            return x == other.x && y == other.y && z == other.z;
        }
        return false;
    }

    /**
     * Determines if this {@link Position} is within the area defined by {@code center} and {@code radius}.
     *
     * @param center The center point of the radius.
     * @param radius The distance to the center point.
     * @return {@code true} if within the radius, {@code false} otherwise.
     */
    public boolean isWithinRadius(Position center, int radius) {
        if (z != center.z) {
            return false;
        }
        int deltaX = center.x - x;
        int deltaY = center.y - y;
        return Math.abs(deltaX) <= radius && Math.abs(deltaY) <= radius;
    }

    /**
     * Determines if this {@link Position} is within the area defined by {@code center} and {@code VIEWING_DISTANCE}.
     *
     * @param center The center point of the radius.
     * @return {@code true} if within the viewable distance, {@code false} otherwise.
     */
    public boolean isViewable(Position center) {
        return isWithinRadius(center, EntityConstants.VIEWING_DISTANCE);
    }

    /**
     * Determines the distance from this {@link Position} to {@code to}.
     *
     * @param to The {@code Position} to calculate the distance to.
     * @return The distance between this {@code Position} and {@code to}.
     */
    public int getDistance(Position to) {
        int deltaX = Math.abs(to.x - x);
        int deltaY = Math.abs(to.y - y);
        return Math.max(deltaX, deltaY);
    }

    /**
     * Returns a new {@link Position} moved by the specified coordinates.
     *
     * @param amountX The {@code X} amount to move.
     * @param amountY The {@code Y} amount to move.
     * @param amountZ The {@code Z} amount to move.
     * @return The new moved instance of {@code Position}.
     */
    public Position move(int amountX, int amountY, int amountZ) {
        return new Position(x + amountX, y + amountY, z + amountZ);
    }

    /**
     * Returns a new {@link Position} moved by the specified coordinates. The {@code Z} value remains unmodified.
     *
     * @param amountX The {@code X} amount to move.
     * @param amountY The {@code Y} amount to move.
     * @return The new moved instance of {@code Position}.
     */
    public Position move(int amountX, int amountY) {
        return move(amountX, amountY, z);
    }

    /**
     * @return The {@code X} region coordinate.
     */
    public int getRegionX() {
        return (x >> 3) - 6;
    }

    /**
     * @return The {@code Y} region coordinate.
     */
    public int getRegionY() {
        return (y >> 3) - 6;
    }

    /**
     * Gets the local {@code X} coordinate relative to {@code base}.
     *
     * @param base The relative base position.
     * @return The local {@code X} coordinate.
     */
    public int getLocalX(Position base) {
        return x - 8 * base.getRegionX();
    }

    /**
     * Gets the local {@code Y} coordinate relative to {@code base}.
     *
     * @param base The relative base position.
     * @return The local {@code Y} coordinate.
     */
    public int getLocalY(Position base) {
        return y - 8 * base.getRegionY();
    }

    /**
     * @return The local {@code X} coordinate relative to this {@link Position}.
     */
    public int getLocalX() {
        return getLocalX(this);
    }

    /**
     * @return The local {@code Y} coordinate relative to this {@link Position}.
     */
    public int getLocalY() {
        return getLocalY(this);
    }

    /**
     * @return The {@code X} coordinate region chunk.
     */
    public int getChunkX() {
        return (x >> 6);
    }

    /**
     * @return The {@code Y} coordinate region chunk.
     */
    public int getChunkY() {
        return (y >> 6);
    }

    /**
     * @return The identifier for the region this {@link Position} is in.
     */
    public int getRegionId() {
        return ((getChunkX() << 8) + getChunkY());
    }

    /**
     * @return The absolute {@code X} coordinate.
     */
    public int getX() {
        return x;
    }

    /**
     * @return The absolute {@code Y} coordinate.
     */
    public int getY() {
        return y;
    }

    /**
     * @return The absolute {@code Z} coordinate.
     */
    public int getZ() {
        return z;
    }
}
