package io.luna.game.model.region;

import com.google.common.base.MoreObjects;
import io.luna.game.model.Position;

/**
 * A model containing the coordinates of a region [8x8 chunks, 64x64 tiles] on the Runescape map.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class RegionCoordinates {

    /**
     * The center x coordinate of this region.
     */
    private final int x;

    /**
     * The center y coordinate of this region.
     */
    private final int y;

    /**
     * The region identifier.
     */
    private final int id;

    /**
     * Creates a new {@link RegionCoordinates}.
     *
     * @param position The position to get the region coordinates of.
     */
    public RegionCoordinates(Position position) {
        this(position.getX() / 64, position.getY() / 64);
    }

    /**
     * Creates a new {@link RegionCoordinates}.
     *
     * @param x The center x coordinate of this region.
     * @param y The center y coordinate of this region.
     */
    private RegionCoordinates(int x, int y) {
        this.x = x;
        this.y = y;
        id = (x * 256) + y;
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
        if (obj instanceof RegionCoordinates) {
            RegionCoordinates other = (RegionCoordinates) obj;
            return id == other.id;
        }
        return false;
    }

    /**
     * @return The coordinates of the region North of this one.
     */
    public RegionCoordinates north() {
        return new RegionCoordinates(x, y + 1);
    }

    /**
     * @return The coordinates of the region South of this one.
     */
    public RegionCoordinates south() {
        return new RegionCoordinates(x, y - 1);
    }

    /**
     * @return The coordinates of the region West of this one.
     */
    public RegionCoordinates west() {
        return new RegionCoordinates(x - 1, y);
    }

    /**
     * @return The coordinates of the region East of this one.
     */
    public RegionCoordinates east() {
        return new RegionCoordinates(x + 1, y);
    }

    /**
     * @return The coordinates of the region North-west of this one.
     */
    public RegionCoordinates northWest() {
        return new RegionCoordinates(x - 1, y + 1);
    }

    /**
     * @return The coordinates of the region North-east of this one.
     */
    public RegionCoordinates northEast() {
        return new RegionCoordinates(x + 1, y + 1);
    }

    /**
     * @return The coordinates of the region South-west of this one.
     */
    public RegionCoordinates southWest() {
        return new RegionCoordinates(x - 1, y - 1);
    }

    /**
     * @return The coordinates of the region South-east of this one.
     */
    public RegionCoordinates southEast() {
        return new RegionCoordinates(x + 1, y - 1);
    }

    /**
     * @return The local x coordinate of position in this region.
     */
    public int getLocalX(Position position) {
        return position.getX() % 64;
    }

    /**
     * @return The local y coordinate of position in this region.
     */
    public int getLocalY(Position position) {
        return position.getY() % 64;
    }

    /**
     * @return The center x coordinate of this region.
     */
    public int getX() {
        return x;
    }

    /**
     * @return The center y coordinate of this region.
     */
    public int getY() {
        return y;
    }

    /**
     * @return The region identifier.
     */
    public int getId() {
        return id;
    }
}