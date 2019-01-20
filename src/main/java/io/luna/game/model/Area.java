package io.luna.game.model;

import com.google.common.base.MoreObjects;
import com.google.common.collect.DiscreteDomain;
import com.google.common.collect.Range;
import io.luna.util.RandomUtils;
import io.luna.util.RangeIterator;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import static com.google.common.base.Preconditions.checkArgument;
import static io.luna.game.model.Position.HEIGHT_LEVELS;

/**
 * A model representing a square-shaped 2D/3D area on the map.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class Area {

    /**
     * Creates an {@link Area} using south-west, north-east and z coordinates.
     */
    public static Area create(int southWestX, int southWestY, int northEastX, int northEastY, Range<Integer> z) {
        return new Area(southWestX, southWestY, northEastX, northEastY, z);
    }

    /**
     * Creates an {@link Area} using south-west and north-east coordinates. This is active across all
     * height levels by default.
     */
    public static Area create(int southWestX, int southWestY, int northEastX, int northEastY) {
        return new Area(southWestX, southWestY, northEastX, northEastY, HEIGHT_LEVELS);
    }

    /**
     * Creates an {@link Area} using center and z coordinates along with a radius.
     */
    public static Area createWithRadius(int x, int y, Range<Integer> z, int radius) {
        return new Area(x - radius, y - radius, x + radius, y + radius, z);
    }

    /**
     * Creates an {@link Area} using center coordinates along with a radius. This is active across all
     * height levels by default.
     */
    public static Area createWithRadius(int x, int y, int radius) {
        return new Area(x - radius, y - radius, x + radius, y + radius, HEIGHT_LEVELS);
    }

    /**
     * The south-west x coordinate.
     */
    private final int southWestX;

    /**
     * The south-west y coordinate.
     */
    private final int southWestY;

    /**
     * The north-east x coordinate.
     */
    private final int northEastX;

    /**
     * The north-east y coordinate.
     */
    private final int northEastY;

    /**
     * The z coordinate range.
     */
    private final Range<Integer> z;

    /**
     * The iterable for z coordinate levels.
     */
    private final transient Iterable<Integer> zIterable = this::zIterator;

    /**
     * Creates a new {@link Area}.
     *
     * @param southWestX The south-west x coordinate.
     * @param southWestY The south-west y coordinate.
     * @param northEastX The north-east x coordinate.
     * @param northEastY The north-east y coordinate.
     * @param z The z coordinate range.
     */
    private Area(int southWestX, int southWestY, int northEastX, int northEastY, Range<Integer> z) {
        checkArgument(northEastX >= southWestX, "northEastX cannot be smaller than southWestX");
        checkArgument(northEastY >= southWestY, "northEastY cannot be smaller than southWestY");
        checkArgument(southWestX >= 0 && southWestY >= 0
                        && z.upperEndpoint() <= HEIGHT_LEVELS.upperEndpoint()
                        && z.lowerEndpoint() >= HEIGHT_LEVELS.lowerEndpoint(),
                "Parameters must be within the bounds of a Position");

        this.southWestX = southWestX;
        this.southWestY = southWestY;
        this.northEastX = northEastX;
        this.northEastY = northEastY;
        this.z = z;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("southWestX", southWestX)
                .add("southWestY", southWestY)
                .add("northEastX", northEastX)
                .add("northEastY", northEastY)
                .add("z", z)
                .toString();
    }

    @Override
    public int hashCode() {
        return Objects.hash(southWestX, southWestY, northEastX, northEastY, z);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Area)) {
            return false;
        }
        
        var other = (Area) obj;
    
        return southWestX == other.southWestX && southWestY == other.southWestY &&
                northEastX == other.northEastX && northEastY == other.northEastY && z.equals(other.z);
    }

    /**
     * Determines if this area contains {@code position}. Runs in O(1) time.
     *
     * @param position The position to lookup.
     * @return {@code true} if {@code position} is within the bounds of this area.
     */
    public boolean contains(Position position) {
        return position.getX() >= southWestX && position.getX() <= northEastX &&
                position.getY() >= southWestY && position.getY() <= northEastY && z.contains(position.getZ());
    }

    /**
     * Determines if {@code entity} is within this area. Runs in O(1) time.
     *
     * @param entity The entity to lookup.
     * @return {@code true} if {@code entity} is within the bounds of this area.
     */
    public boolean contains(Entity entity) {
        return contains(entity.getPosition());
    }

    /**
     * Computes and returns a <strong>new</strong> list of positions that make up this area.
     *
     * @return A list of every {@link Position} in this area.
     */
    public List<Position> toList() {
        List<Position> toList = new ArrayList<>(size());
        
        for (int x = southWestX; x <= northEastX; x++) {
            for (int y = southWestY; y <= northEastY; y++) {
                for (int z : zIterable) {
                    toList.add(new Position(x, y, z));
                }
            }
        }
        
        return toList;
    }

    /**
     * Returns a random position from this area.
     *
     * @return A random position from this area.
     */
    public Position random() {
        int randomX = RandomUtils.inclusive(northEastX - southWestX) + southWestX;
        int randomY = RandomUtils.inclusive(northEastY - southWestY) + southWestY;
        int randomZ = RandomUtils.inclusive(z.lowerEndpoint(), z.upperEndpoint());
        return new Position(randomX, randomY, randomZ);
    }

    /**
     * Returns the center of this area.
     *
     * @return The center position.
     */
    public Position center() {
        int halfWidth = width() / 2;
        int centerX = southWestX + halfWidth;
        int centerY = southWestY + halfWidth;
        return new Position(centerX, centerY);
    }

    /**
     * Returns the length of this area.
     *
     * @return The length, in tiles.
     */
    public int length() {
        // Areas are inclusive to base coordinates, so we add 1.
        return (northEastY - southWestY) + 1;
    }

    /**
     * Returns the width of this area.
     *
     * @return The width, in tiles.
     */
    public int width() {
        // Areas are inclusive to base coordinates, so we add 1.
        return (northEastX - southWestX) + 1;
    }

    /**
     * Returns the size of this area (length * width * height).
     *
     * @return The total size, in tiles.
     */
    public int size() {
        int area = length() * width();
        int size = area;
        
        for (int z : zIterable) {
            size = size + area;
        }
        
        return size;
    }

    /**
     * Creates a returns a new {@link RangeIterator} that iterates over z coordinates.
     *
     * @return The z coordinate iterator.
     */
    public Iterator<Integer> zIterator() {
        return new RangeIterator<>(z, DiscreteDomain.integers());
    }

    /**
     * @return The south-west x coordinate.
     */
    public int getSouthWestX() {
        return southWestX;
    }

    /**
     * @return The south-west y coordinate.
     */
    public int getSouthWestY() {
        return southWestY;
    }

    /**
     * @return The north-east x coordinate.
     */
    public int getNorthEastX() {
        return northEastX;
    }

    /**
     * @return The north-east y coordinate.
     */
    public int getNorthEastY() {
        return northEastY;
    }

    /**
     * @return The z coordinate.
     */
    public Range<Integer> getZ() {
        return z;
    }
}
