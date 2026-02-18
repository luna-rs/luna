package io.luna.game.model;

import com.google.common.base.MoreObjects;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Represents a 64x64 tile segment of the RuneScape world map.
 * <p>
 * A {@code Region} is a high-level spatial partition used to group tiles into fixed-size square areas. Regions are
 * identified by a packed integer id computed as:
 * <pre>
 * id = x * 256 + y
 * </pre>
 * <p>
 * where {@code x} and {@code y} represent region coordinates.
 * <p>
 * Regions are primarily used for:
 * <ul>
 *     <li>Map streaming and chunk loading</li>
 *     <li>Collision partitioning</li>
 *     <li>World partition lookups</li>
 *     <li>Bulk tile operations</li>
 * </ul>
 *
 * @author lare96
 * @see Position
 * @see Locatable
 */
public final class Region implements Locatable {

    /**
     * The width and height of a region in tiles.
     */
    public static final int SIZE = 64;

    /**
     * The region X coordinate.
     */
    private final int x;

    /**
     * The region Y coordinate.
     */
    private final int y;

    /**
     * The packed region identifier.
     */
    private final int id;

    /**
     * Creates a new {@link Region} from a tile {@link Position}.
     *
     * @param position The tile position used to compute region coordinates.
     */
    public Region(Position position) {
        this(position.getX() / SIZE, position.getY() / SIZE);
    }

    /**
     * Creates a new {@link Region} from a packed region id.
     *
     * @param id The packed region identifier.
     */
    public Region(int id) {
        this.id = id;
        this.x = id / 256;
        this.y = id - (x * 256);
    }

    /**
     * Creates a new {@link Region} from region coordinates.
     *
     * @param x The region X coordinate.
     * @param y The region Y coordinate.
     */
    public Region(int x, int y) {
        this.x = x;
        this.y = y;
        this.id = x * 256 + y;
    }

    /**
     * Determines whether the given {@link Position} lies within this region.
     *
     * @param position The tile position.
     * @return {@code true} if the position belongs to this region.
     */
    @Override
    public boolean contains(Position position) {
        return id == position.getRegion().id;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("id", id)
                .add("x", x)
                .add("y", y)
                .toString();
    }

    /**
     * Regions are uniquely identified by their packed id.
     *
     * @return The region id.
     */
    @Override
    public int hashCode() {
        return id;
    }

    /**
     * Regions are equal if their packed ids match.
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj instanceof Region) {
            Region other = (Region) obj;
            return id == other.id;
        }
        return false;
    }

    @Override
    public Position absLocation() {
        return getAbsPosition();
    }

    /**
     * Checks whether this region is within a given region-distance of another region.
     * <p>
     * Distance is measured in region units (not tiles) using axis-aligned comparison.
     *
     * @param region The other region.
     * @param distance The maximum allowed region delta.
     * @return {@code true} if within distance.
     */
    public boolean isWithinDistance(Region region, int distance) {
        int deltaX = Math.abs(region.x - x);
        int deltaY = Math.abs(region.y - y);
        return deltaX <= distance && deltaY <= distance;
    }

    /**
     * @return The region directly north of this region.
     */
    public Region getNorthRegion() {
        return new Region(id + 1);
    }

    /**
     * @return The region directly south of this region.
     */
    public Region getSouthRegion() {
        return new Region(id - 1);
    }

    /**
     * @return The region directly west of this region.
     */
    public Region getWestRegion() {
        return new Region(id - 256);
    }

    /**
     * @return The region directly east of this region.
     */
    public Region getEastRegion() {
        return new Region(id + 256);
    }

    /**
     * @return The north-east adjacent region.
     */
    public Region getNERegion() {
        return new Region(id + 257);
    }

    /**
     * @return The north-west adjacent region.
     */
    public Region getNWRegion() {
        return new Region(id - 255);
    }

    /**
     * @return The south-east adjacent region.
     */
    public Region getSERegion() {
        return new Region(id + 255);
    }

    /**
     * @return The south-west adjacent region.
     */
    public Region getSWRegion() {
        return new Region(id - 257);
    }

    /**
     * Returns the eight surrounding regions adjacent to this region.
     *
     * @return A set of neighboring regions.
     */
    public Set<Region> getSurroundingRegions() {
        Set<Region> regions = new HashSet<>(8);
        regions.add(getNorthRegion());
        regions.add(getSouthRegion());
        regions.add(getWestRegion());
        regions.add(getEastRegion());
        regions.add(getNERegion());
        regions.add(getNWRegion());
        regions.add(getSERegion());
        regions.add(getSWRegion());
        return regions;
    }

    /**
     * Returns all tile positions within this region.
     * <p>
     * This operation is expensive ({@code 4096} allocations) and should be used sparingly.
     *
     * @return A list containing every tile in this region.
     */
    public List<Position> getAllPositions() {
        Position base = getAbsPosition();
        List<Position> tiles = new ArrayList<>(SIZE * SIZE);

        for (int dx = 0; dx < SIZE; dx++) {
            for (int dy = 0; dy < SIZE; dy++) {
                tiles.add(new Position(
                        base.getX() + dx,
                        base.getY() + dy,
                        base.getZ()
                ));
            }
        }

        return tiles;
    }

    /**
     * Returns the bottom-left tile of this region.
     *
     * @return The absolute base {@link Position}.
     */
    public Position getAbsPosition() {
        return new Position(x * SIZE, y * SIZE);
    }

    /**
     * @return The region X coordinate.
     */
    public int getX() {
        return x;
    }

    /**
     * @return The region Y coordinate.
     */
    public int getY() {
        return y;
    }

    /**
     * @return The packed region identifier.
     */
    public int getId() {
        return id;
    }
}
