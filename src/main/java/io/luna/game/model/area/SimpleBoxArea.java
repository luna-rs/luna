package io.luna.game.model.area;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import io.luna.game.model.Position;
import io.luna.util.RandomUtils;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * A rectangular {@link Area} defined by inclusive south-west and north-east bounds.
 * <p>
 * This area represents an axis-aligned rectangle in tile space. Both the minimum coordinates ({@code southWestX},
 * {@code southWestY}) and maximum coordinates ({@code northEastX}, {@code northEastY}) are included in the area.
 * <p>
 * Construction validates that the bounds are not inverted. Prefer {@link Area#of(int, int, int, int)} when coordinates
 * may need to be normalized first.
 *
 * @author lare96
 */
public class SimpleBoxArea extends Area {

    /**
     * The inclusive western x coordinate.
     */
    private final int southWestX;

    /**
     * The inclusive southern y coordinate.
     */
    private final int southWestY;

    /**
     * The inclusive eastern x coordinate.
     */
    private final int northEastX;

    /**
     * The inclusive northern y coordinate.
     */
    private final int northEastY;

    /**
     * The cached north-east corner of this area.
     */
    private final Position northEast;

    /**
     * The cached north-west corner of this area.
     */
    private final Position northWest;

    /**
     * The cached south-east corner of this area.
     */
    private final Position southEast;

    /**
     * The cached south-west corner of this area.
     */
    private final Position southWest;

    /**
     * A set containing the region ids of this area's corner positions.
     */
    private final ImmutableSet<Integer> touchedRegions;

    /**
     * Creates a new {@link SimpleBoxArea}.
     *
     * @param southWestX The inclusive western x coordinate.
     * @param southWestY The inclusive southern y coordinate.
     * @param northEastX The inclusive eastern x coordinate.
     * @param northEastY The inclusive northern y coordinate.
     * @throws IllegalArgumentException if the north-east bounds are smaller than the south-west bounds.
     */
    SimpleBoxArea(int southWestX, int southWestY, int northEastX, int northEastY) {
        checkArgument(northEastX >= southWestX, "northEastX cannot be smaller than southWestX");
        checkArgument(northEastY >= southWestY, "northEastY cannot be smaller than southWestY");

        this.southWestX = southWestX;
        this.southWestY = southWestY;
        this.northEastX = northEastX;
        this.northEastY = northEastY;

        northEast = new Position(northEastX, northEastY);
        northWest = new Position(southWestX, northEastY);
        southEast = new Position(northEastX, southWestY);
        southWest = new Position(southWestX, southWestY);

        touchedRegions = ImmutableSet.of(northEast.getRegionId(), northWest.getRegionId(), southWest.getRegionId(),
                southEast.getRegionId());
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

    @Override
    public boolean contains(Position position) {
        return position.getX() >= southWestX &&
                position.getX() <= northEastX &&
                position.getY() >= southWestY &&
                position.getY() <= northEastY;
    }

    @Override
    public int size() {
        return length() * width();
    }

    @Override
    public Position randomPosition() {
        int randomX = RandomUtils.exclusive(width()) + southWestX;
        int randomY = RandomUtils.exclusive(length()) + southWestY;
        return new Position(randomX, randomY);
    }

    @Override
    public ImmutableList<Position> computePositions() {
        ImmutableList.Builder<Position> list = ImmutableList.builder();

        for (int x = southWestX; x <= northEastX; x++) {
            for (int y = southWestY; y <= northEastY; y++) {
                list.add(new Position(x, y));
            }
        }

        return list.build();
    }

    /**
     * @return The vertical tile length of this area.
     */
    public int length() {
        return (northEastY - southWestY) + 1;
    }

    /**
     * @return The horizontal tile width of this area.
     */
    public int width() {
        return (northEastX - southWestX) + 1;
    }

    /**
     * Returns the center position of this area.
     * <p>
     * If the area has an even width or length, the center is rounded toward the south-west corner due to integer
     * division.
     *
     * @return The center position of this area.
     */
    public Position getCenterPosition() {
        int centerX = southWestX + ((northEastX - southWestX) / 2);
        int centerY = southWestY + ((northEastY - southWestY) / 2);
        return new Position(centerX, centerY);
    }

    /**
     * Returns the smallest tile radius from the center that fully covers this area.
     * <p>
     * The radius is measured from {@link #getCenterPosition()} to the farthest edge
     * of the rectangle.
     *
     * @return The tile radius required to cover this area from its center.
     */
    public int getTileRadius() {
        Position center = getCenterPosition();

        int west = center.getX() - southWestX;
        int east = northEastX - center.getX();
        int south = center.getY() - southWestY;
        int north = northEastY - center.getY();

        return Math.max(Math.max(west, east), Math.max(south, north));
    }

    /**
     * @return A set containing the region ids of this area's corner positions.
     */
    public ImmutableSet<Integer> getTouchedRegions() {
        return touchedRegions;
    }

    /**
     * @return The cached north-east position.
     */
    public Position getNorthEast() {
        return northEast;
    }

    /**
     * @return The cached north-west position.
     */
    public Position getNorthWest() {
        return northWest;
    }

    /**
     * @return The cached south-east position.
     */
    public Position getSouthEast() {
        return southEast;
    }

    /**
     * @return The cached south-west position.
     */
    public Position getSouthWest() {
        return southWest;
    }
}