package io.luna.game.model;

import com.google.common.base.MoreObjects;

/**
 * A {@link Location} made up of 64x64 tiles on the Runescape map.
 *
 * @author lare96
 */
public final class Region implements Location {

    /**
     * The length and width.
     */
    public static final int SIZE = 64;

    /**
     * The multiplicative factor to get the region ID.
     */
    public static final int MULTIPLICATIVE_FACTOR = 256;

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
     * Creates a new {@link Region}.
     *
     * @param position The base position.
     */
    public Region(Position position) {
        this(position.getX() / SIZE, position.getY() / SIZE);
    }

    /**
     * Creates a new {@link Region}.
     *
     * @param id
     */
    public Region(int id) {
        this.id = id;
        x = id / MULTIPLICATIVE_FACTOR;
        y = id - (x * MULTIPLICATIVE_FACTOR);
    }

    /**
     * Creates a new {@link Region}.
     *
     * @param x The center x coordinate.
     * @param y The center y coordinate.
     */
    public Region(int x, int y) {
        this.x = x;
        this.y = y;
        id = x * MULTIPLICATIVE_FACTOR + y;
    }

    @Override
    public boolean contains(Position position) {
        return id == position.getRegion().id;
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
        if (obj instanceof Region) {
            Region other = (Region) obj;
            return id == other.id;
        }
        return false;
    }

    /**
     * Returns the base {@link Position} in this region.
     *
     * @return The base position.
     */
    public Position getBasePosition() {
        return new Position(x * SIZE, y * SIZE);
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