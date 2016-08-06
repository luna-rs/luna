package io.luna.game.model;

import com.google.common.base.MoreObjects;
import io.luna.util.RandomUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * A chunk of {@link Position}s geometrically represented on the map as a 2D square or rectangle.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class Area {

    /**
     * Creates a standard Area. Will use the south-west and north-east coordinates to construct a square or rectangle.
     */
    public static Area create(int southWestX, int southWestY, int northEastX, int northEastY, int z) {
        return new Area(southWestX, southWestY, northEastX, northEastY, z);
    }

    /**
     * Creates a standard Area. Will use {@code (x, y, z)} coordinates to construct a square or rectangle with a specific
     * radius.
     */
    public static Area createWithRadius(int x, int y, int z, int radius) {
        return new Area(x - radius, y - radius, x + radius, y + radius, z);
    }

    /**
     * The south-west {@code x} coordinate of the area.
     */
    private final int southWestX;

    /**
     * The south-west {@code y} coordinate of the area.
     */
    private final int southWestY;

    /**
     * The north-east {@code x} coordinate of the area.
     */
    private final int northEastX;

    /**
     * The north-east {@code y} coordinate of the area.
     */
    private final int northEastY;

    /**
     * The {@code z} coordinate of the area.
     */
    private final int z;

    /**
     * Creates a new {@link Area}.
     *
     * @param southWestX The south-west {@code x} coordinate of the area.
     * @param southWestY The south-west {@code y} coordinate of the area.
     * @param northEastX The north-east {@code x} coordinate of the area.
     * @param northEastY The north-east {@code y} coordinate of the area.
     * @param z The {@code z} coordinate of the area.
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
     * Determines if this Area contains {@code position}. Does not need to compute any positions, therefore runs in O(1)
     * time.
     *
     * @param position The position to determine this for.
     * @return {@code true} if contained in this Area, {@code false} otherwise.
     */
    public boolean contains(Position position) {
        return position.getX() >= southWestX &&
            position.getX() <= northEastX &&
            position.getY() >= southWestY &&
            position.getY() <= northEastY &&
            position.getZ() == z;
    }

    /**
     * Computes all of the positions within this Area. This function runs in approximately O(n*m) where {@code n} depends on
     * the length and {@code m} the width of the Area. Please note that the result of this function <strong>is not</strong>
     * cached, meaning a new list will be computed and returned on each invocation.
     *
     * @return An {@link ArrayList} containing the positions within this Area.
     */
    public List<Position> computePositions() {
        List<Position> toList = new ArrayList<>(computeSize());

        for (int x = southWestX; x <= northEastX; x++) {
            for (int y = southWestY; y <= northEastY; y++) {
                toList.add(new Position(x, y, z));
            }
        }
        return toList;
    }

    /**
     * Computes and returns a random Position within this Area. It is <strong>much</strong> cheaper to call this method
     * rather than randomly selecting an element from {@code computePositions()}.
     *
     * @return The random position.
     */
    public Position computeRandomPosition() {
        int computeX = RandomUtils.inclusive(northEastX - southWestX) + southWestX;
        int computeY = RandomUtils.inclusive(northEastY - southWestY) + southWestY;
        return new Position(computeX, computeY, z);
    }

    /**
     * @return The length of this Area.
     */
    public int computeLength() {
        // Areas are inclusive to base coordinates, so we add 1.
        return (northEastY - southWestY) + 1;
    }

    /**
     * @return The width of this Area.
     */
    public int computeWidth() {
        // Areas are inclusive to base coordinates, so we add 1.
        return (northEastX - southWestX) + 1;
    }

    /**
     * @return The size, or in other words -- the geometric area of this Area.
     */
    public int computeSize() {
        return computeLength() * computeWidth();
    }

    /**
     * @return The south-west {@code x} coordinate of the area.
     */
    public int getSouthWestX() {
        return southWestX;
    }

    /**
     * @return The south-west {@code y} coordinate of the area.
     */
    public int getSouthWestY() {
        return southWestY;
    }

    /**
     * @return The north-east {@code x} coordinate of the area.
     */
    public int getNorthEastX() {
        return northEastX;
    }

    /**
     * @return The north-east {@code y} coordinate of the area.
     */
    public int getNorthEastY() {
        return northEastY;
    }

    /**
     * @return The {@code z} coordinate of the area.
     */
    public int getZ() {
        return z;
    }
}
