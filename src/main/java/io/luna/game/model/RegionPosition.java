package io.luna.game.model;

import com.google.common.base.MoreObjects;

/**
 * A model representing the coordinates of a Region (64x64 tiles) on the Runescape map.
 *
 * @author lare96 <http://github.com/lare96>
 */
public final class RegionPosition {

    /**
     * The length and width.
     */
    public static final int SIZE = 64;

    /**
     * The multiplicative factor to get the region ID.
     */
    public static final int MULTIPLICATIVE_FACTOR = 256;

    /**
     * Returns the base {@link Position} from region {@code id}.
     *
     * @param id The region id.
     * @return The position in the region.
     */
    public static Position getPositionInRegion(int id) {
        int regionX = id / MULTIPLICATIVE_FACTOR;
        int regionY = id - (regionX * MULTIPLICATIVE_FACTOR);
        return new Position(regionX * SIZE, regionY * SIZE);
    }

    /**
     * The center x coordinate.
     */
    private final int x;

    /**
     * The center y coordinate.
     */
    private final int y;

    /**
     * The identifier.
     */
    private final int id;

    /**
     * Creates a new {@link RegionPosition}.
     *
     * @param position The base position.
     */
    public RegionPosition(Position position) {
        this(position.getX() / SIZE, position.getY() / SIZE);
    }

    /**
     * Creates a new {@link RegionPosition}.
     *
     * @param x The center x coordinate.
     * @param y The center y coordinate.
     */
    private RegionPosition(int x, int y) {
        this.x = x;
        this.y = y;
        id = (x * MULTIPLICATIVE_FACTOR) + y;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).add("id", id).add("x", x).add("y", y).toString();
    }

    @Override
    public int hashCode() {
        return id;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof RegionPosition) {
            RegionPosition other = (RegionPosition) obj;
            return id == other.id;
        }
        return false;
    }

    /**
     * Returns the local x coordinate of {@code position} in this region.
     *
     * @param position The position.
     * @return The local x coordinate.
     */
    public int getLocalX(Position position) {
        return position.getX() % SIZE;
    }

    /**
     * Returns the local y coordinate of {@code position} in this region.
     *
     * @param position The position.
     * @return The local y coordinate.
     */
    public int getLocalY(Position position) {
        return position.getY() % SIZE;
    }

    /**
     * @return The center x coordinate.
     */
    public int getX() {
        return x;
    }

    /**
     * @return The center y coordinate.
     */
    public int getY() {
        return y;
    }

    /**
     * @return The identifier.
     */
    public int getId() {
        return id;
    }
}