package io.luna.game.model.area;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import io.luna.game.model.Position;
import io.luna.util.RandomUtils;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * A rectangular {@link Area} defined by two inclusive corner points.
 * <p>
 * This area represents an axis-aligned "box" in tile-space, bounded by an inclusive south-west corner
 * ({@code southWestX}, {@code southWestY}) and an inclusive north-east corner ({@code northEastX}, {@code northEastY}).
 * <p>
 * Coordinate validation is performed on construction to ensure the bounds are not inverted.
 * <p>
 * <b>Inclusive bounds:</b> {@link #contains(Position)} uses {@code <=} for the maximum coordinates, meaning the edges
 * are included. {@link #length()} and {@link #width()} therefore add {@code +1}.
 *
 * @author lare96
 */
public class SimpleBoxArea extends Area {

    /**
     * The inclusive south-west x coordinate.
     */
    private final int southWestX;

    /**
     * The inclusive south-west y coordinate.
     */
    private final int southWestY;

    /**
     * The inclusive north-east x coordinate.
     */
    private final int northEastX;

    /**
     * The inclusive north-east y coordinate.
     */
    private final int northEastY;

    /**
     * Creates a new {@link SimpleBoxArea}.
     * <p>
     * Prefer using {@link Area#of(int, int, int, int)} to automatically normalize inverted coordinates.
     *
     * @param southWestX The inclusive south-west x coordinate.
     * @param southWestY The inclusive south-west y coordinate.
     * @param northEastX The inclusive north-east x coordinate.
     * @param northEastY The inclusive north-east y coordinate.
     * @throws IllegalArgumentException if {@code northEastX < southWestX} or {@code northEastY < southWestY}.
     */
    SimpleBoxArea(int southWestX, int southWestY, int northEastX, int northEastY) {
        checkArgument(northEastX >= southWestX, "northEastX cannot be smaller than southWestX");
        checkArgument(northEastY >= southWestY, "northEastY cannot be smaller than southWestY");
        this.southWestX = southWestX;
        this.southWestY = southWestY;
        this.northEastX = northEastX;
        this.northEastY = northEastY;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SimpleBoxArea that = (SimpleBoxArea) o;
        return southWestX == that.southWestX &&
                southWestY == that.southWestY &&
                northEastX == that.northEastX &&
                northEastY == that.northEastY;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(southWestX, southWestY, northEastX, northEastY);
    }

    /**
     * Checks whether {@code position} lies inside this rectangle (inclusive of edges).
     *
     * @param position The position to test.
     * @return {@code true} if {@code position} is within the inclusive bounds.
     */
    @Override
    public boolean contains(Position position) {
        return position.getX() >= southWestX &&
                position.getX() <= northEastX &&
                position.getY() >= southWestY &&
                position.getY() <= northEastY;
    }

    /**
     * Returns the number of tiles covered by this area (inclusive).
     *
     * @return {@code length() * width()}.
     */
    @Override
    public int size() {
        return length() * width();
    }

    /**
     * Picks a uniformly random position inside this rectangle.
     * <p>
     * This implementation is O(1) and does not require enumerating all positions.
     *
     * @return A random position within the inclusive bounds.
     */
    @Override
    public Position randomPosition() {
        int randomX = RandomUtils.exclusive(width()) + southWestX;
        int randomY = RandomUtils.exclusive(length()) + southWestY;
        return new Position(randomX, randomY);
    }

    /**
     * Enumerates every tile inside this rectangle.
     *
     * @return An immutable list containing every position in row-major order.
     */
    @Override
    public ImmutableList<Position> computePositions() {
        ImmutableList.Builder<Position> list = ImmutableList.builder();
        for (int x = southWestX; x <= northEastX; x++) {
            for (int y = southWestY; y <= northEastY; y++) {
                // 'contains' is redundant here but kept for clarity/consistency.
                Position position = new Position(x, y);
                if (contains(position)) {
                    list.add(position);
                }
            }
        }
        return list.build();
    }

    /**
     * Returns the height of this rectangle in tiles (inclusive).
     *
     * @return {@code (northEastY - southWestY) + 1}.
     */
    public int length() {
        return (northEastY - southWestY) + 1;
    }

    /**
     * Returns the width of this rectangle in tiles (inclusive).
     *
     * @return {@code (northEastX - southWestX) + 1}.
     */
    public int width() {
        return (northEastX - southWestX) + 1;
    }
}
