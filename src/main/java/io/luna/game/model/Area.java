package io.luna.game.model;

import com.google.common.base.MoreObjects;
import io.luna.util.RandomUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * A group of positions represented geometrically as a square, cube, rectangle, or rectangular prism.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class Area {

    /**
     * Creates an area using south-west, north-east and z coordinates.
     */
    public static Area create(int southWestX, int southWestY, int northEastX, int northEastY, int z) {
        return new Area(southWestX, southWestY, northEastX, northEastY, z);
    }

    /**
     * Creates an area using south-west and north-east coordinates.
     */
    public static Area create(int southWestX, int southWestY, int northEastX, int northEastY) {
        return new Area(southWestX, southWestY, northEastX, northEastY, 0);
    }

    /**
     * Creates an area using center and z coordinates along with a radius.
     */
    public static Area createWithRadius(int x, int y, int z, int radius) {
        return new Area(x - radius, y - radius, x + radius, y + radius, z);
    }

    /**
     * Creates an area using center coordinates along with a radius.
     */
    public static Area createWithRadius(int x, int y, int radius) {
        return new Area(x - radius, y - radius, x + radius, y + radius, 0);
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
     * The z coordinate.
     */
    private final int z;

    /**
     * Creates a new {@link Area}.
     *
     * @param southWestX The south-west x coordinate.
     * @param southWestY The south-west y coordinate.
     * @param northEastX The north-east x coordinate.
     * @param northEastY The north-east y coordinate.
     * @param z The z coordinate.
     */
    private Area(int southWestX, int southWestY, int northEastX, int northEastY, int z) {
        checkArgument(northEastX >= southWestX, "northEastX cannot be smaller than southWestX");
        checkArgument(northEastY >= southWestY, "northEastY cannot be smaller than southWestY");
        checkArgument(southWestX >= 0 && southWestY >= 0 &&
            northEastX >= 0 && northEastY >= 0 &&
            z >= 0 && z <= 3, "Parameters must conform to rules of Position.class");

        this.southWestX = southWestX;
        this.southWestY = southWestY;
        this.northEastX = northEastX;
        this.northEastY = northEastY;
        this.z = z;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).
            add("southWestX", southWestX).
            add("southWestY", southWestY).
            add("northEastX", northEastX).
            add("northEastY", northEastY).
            add("z", z).toString();
    }

    @Override
    public int hashCode() {
        return Objects.hash(southWestX, southWestY, northEastX, northEastY, z);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof Area) {
            Area other = (Area) obj;
            return southWestX == other.southWestX &&
                southWestY == other.southWestY &&
                northEastX == other.northEastX &&
                northEastY == other.northEastY &&
                z == other.z;
        }
        return false;
    }

    /**
     * Determines if this area contains {@code position}. Runs in O(1) time.
     */
    public boolean contains(Position position) {
        return position.getX() >= southWestX &&
            position.getX() <= northEastX &&
            position.getY() >= southWestY &&
            position.getY() <= northEastY &&
            position.getZ() == z;
    }

    /**
     * Determines if {@code entity} is within this area. Runs in O(1) time.
     */
    public boolean contains(Entity entity) {
        return contains(entity.getPosition());
    }

    /**
     * Computes and returns a <strong>new</strong> list of positions that make up this
     * area. Runs in approx. O(n*m) time.
     */
    public List<Position> toList() {
        List<Position> toList = new ArrayList<>(size());

        for (int x = southWestX; x <= northEastX; x++) {
            for (int y = southWestY; y <= northEastY; y++) {
                toList.add(new Position(x, y, z));
            }
        }
        return toList;
    }

    /**
     * Returns a random position from this area.
     */
    public Position random() {
        int randomX = RandomUtils.inclusive(northEastX - southWestX) + southWestX;
        int randomY = RandomUtils.inclusive(northEastY - southWestY) + southWestY;
        return new Position(randomX, randomY, z);
    }

    /**
     * Returns the center of this area.
     */
    public Position center() {
        int halfWidth = width() / 2;
        int centerX = southWestX + halfWidth;
        int centerY = southWestY + halfWidth;
        return new Position(centerX, centerY);
    }

    /**
     * Returns the length of this area.
     */
    public int length() {
        /* Areas are inclusive to base coordinates, so we add 1 */
        return (northEastY - southWestY) + 1;
    }

    /**
     * Returns the width of this area.
     */
    public int width() {
        /* Areas are inclusive to base coordinates, so we add 1. */
        return (northEastX - southWestX) + 1;
    }

    /**
     * Returns the size of this area (length * width).
     */
    public int size() {
        return length() * width();
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
    public int getZ() {
        return z;
    }
}
