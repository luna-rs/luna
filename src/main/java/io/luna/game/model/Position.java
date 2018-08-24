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

    // TODO Read up on tile system and confirm if all functions are named correctly.

    /**
     * The x coordinate.
     */
    private final int x;

    /**
     * The y coordinate.
     */
    private final int y;

    /**
     * The z coordinate.
     */
    private final int z;

    /**
     * Creates a new {@link Position}.
     *
     * @param x The x coordinate.
     * @param y The y coordinate.
     * @param z The z coordinate.
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
     * Creates a new {@link Position} with {@code 0} as the z coordinate.
     *
     * @param x The x coordinate.
     * @param y The y coordinate.
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
     * Determines if this position is within the given distance of another position.
     */
    public boolean isWithinDistance(Position pos, int distance) {
        if (z != pos.z) {
            return false;
        }
        int dist = getDistance(pos);
        return dist <= distance;
    }

    /**
     * Determines if this position is viewable from {@code position}.
     */
    public boolean isViewable(Position position) {
        return isWithinDistance(position, EntityConstants.VIEWING_DISTANCE);
    }

    /**
     * Returns the distance between this position and {@code position}.
     */
    public int getDistance(Position position) {
        int deltaX = Math.abs(position.x - x);
        int deltaY = Math.abs(position.y - y);
        return Math.max(deltaX, deltaY);
    }

    /**
     * Returns a new position translated by the specified amounts.
     */
    public Position translate(int amountX, int amountY, int amountZ) {
        return new Position(x + amountX, y + amountY, z + amountZ);
    }

    /**
     * Returns a new position translated by the specified amounts. The z coordinate will remain
     * the same.
     */
    public Position translate(int amountX, int amountY) {
        return translate(amountX, amountY, z);
    }

    /**
     * Returns the x coordinate of the position's region.
     */
    public int getRegionX() {
        return (x >> 3) - 6;
    }

    /**
     * Returns the y coordinate of the position's region.
     */
    public int getRegionY() {
        return (y >> 3) - 6;
    }

    /**
     * Returns the local x coordinate relative to {@code base}.
     */
    public int getLocalX(Position base) {
        return x - (base.getRegionX() << 3);
    }

    /**
     * Returns the local y coordinate relative to {@code base}.
     */
    public int getLocalY(Position base) {
        return y - (base.getRegionY() << 3);
    }

    /**
     * Returns the local x coordinate relative to this position.
     */
    public int getLocalX() {
        return getLocalX(this);
    }

    /**
     * Returns the local y coordinate relative to this position.
     */
    public int getLocalY() {
        return getLocalY(this);
    }

    /**
     * Returns the x coordinate of this position's region chunk.
     */
    public int getChunkX() {
        return (x >> 6);
    }

    /**
     * Returns the y coordinate of this position's region chunk.
     */
    public int getChunkY() {
        return (y >> 6);
    }

    /**
     * Returns the identifier of this position's region.
     */
    public int getRegionId() {
        return ((getChunkX() << 8) + getChunkY());
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

    /**
     * @return The z coordinate.
     */

    public int getZ() {
        return z;
    }
}
