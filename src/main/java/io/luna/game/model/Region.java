package io.luna.game.model;

import com.google.common.base.MoreObjects;

/**
 * A {@link Locatable} made up of 64x64 tiles on the Runescape map.
 *
 * @author lare96
 */
public final class Region implements Locatable {

    /**
     * The length and width.
     */
    public static final int SIZE = 64;

    /**
     * The {@code x}  coordinate.
     */
    private final int x;

    /**
     * The {@code y}  coordinate.
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
     * @param id The identifier.
     */
    public Region(int id) {
        this.id = id;
        x = id / 256;
        y = id - (x * 256);
    }

    /**
     * Creates a new {@link Region}.
     *
     * @param x The {@code x}  coordinate.
     * @param y The {@code y}  coordinate.
     */
    public Region(int x, int y) {
        this.x = x;
        this.y = y;
        id = x * 256 + y;
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

    @Override
    public Position location() {
        return getAbsPosition();
    }

    /**
     * Checks if {@code region} is within distance of {@code distance} (in regions).
     */
    public boolean isWithinDistance(Region region, int distance) {
        int deltaX = Math.abs(region.getX() - x);
        int deltaY = Math.abs(region.getY() - y);
        return deltaX <= distance && deltaY <= distance;
    }
// todo ne + 257, nw - 255
    public Region getNorthRegion() {
        return new Region(id + 1);
    }
    public Region getSouthRegion() {
        return new Region(id - 1);
    }
    public Region getWestRegion() {
        return new Region(id - 256);
    }
    public Region getEastRegion() {
        return new Region(id + 256);
    }
    /**
     * Returns the base bottom-left {@link Position} in this region.
     *
     * @return The base position.
     */
    public Position getAbsPosition() {
        return new Position(x * SIZE, y * SIZE);
    }

    /**
     * @return The {@code x}  coordinate.
     */
    public int getX() {
        return x;
    }

    /**
     * @return The {@code y}  coordinate.
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