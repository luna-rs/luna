package io.luna.game.model.area;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableSet;
import io.luna.game.model.Position;
import io.luna.util.RandomUtils;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * The simple box implementation.
 */
public class SimpleBoxArea extends Area {

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
     * Creates a new {@link SimpleBoxArea}.
     *
     * @param southWestX The south-west x coordinate.
     * @param southWestY The south-west y coordinate.
     * @param northEastX The north-east x coordinate.
     * @param northEastY The north-east y coordinate.
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
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SimpleBoxArea that = (SimpleBoxArea) o;
        return southWestX == that.southWestX && southWestY == that.southWestY &&
                northEastX == that.northEastX && northEastY == that.northEastY;
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
    public ImmutableSet<Position> computePositionSet() {
        ImmutableSet.Builder<Position> set = ImmutableSet.builder();
        for (int x = southWestX; x <= northEastX; x++) {
            for (int y = southWestY; y <= northEastY; y++) {
                set.add(new Position(x, y));
            }
        }
        return set.build();
    }

    /**
     * Returns the length of this area.
     */
    public int length() {
        // Areas are inclusive to base coordinates, so we add 1.
        return (northEastY - southWestY) + 1;
    }

    /**
     * Returns the width of this area.
     */
    public int width() {
        // Areas are inclusive to base coordinates, so we add 1.
        return (northEastX - southWestX) + 1;
    }
}