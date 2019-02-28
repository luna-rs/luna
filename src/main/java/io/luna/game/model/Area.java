package io.luna.game.model;

import io.luna.game.model.mob.Player;
import io.luna.util.RandomUtils;

import java.util.Objects;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * A model representing a square-shaped 2D/3D area on the map. Usually registered with an {@link AreaManager}.
 *
 * @author lare96 <http://github.org/lare96>
 */
public class Area {

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
     * Creates a new {@link Area}.
     *
     * @param southWestX The south-west x coordinate.
     * @param southWestY The south-west y coordinate.
     * @param northEastX The north-east x coordinate.
     * @param northEastY The north-east y coordinate.
     */
    public Area(int southWestX, int southWestY, int northEastX, int northEastY) {
        checkArgument(northEastX >= southWestX, "northEastX cannot be smaller than southWestX");
        checkArgument(northEastY >= southWestY, "northEastY cannot be smaller than southWestY");
        this.southWestX = southWestX;
        this.southWestY = southWestY;
        this.northEastX = northEastX;
        this.northEastY = northEastY;
    }

    @Override
    public int hashCode() {
        return Objects.hash(southWestX, southWestY, northEastX, northEastY);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (obj instanceof Area) {
            var other = (Area) obj;
            return southWestX == other.southWestX &&
                    southWestY == other.southWestY &&
                    northEastX == other.northEastX &&
                    northEastY == other.northEastY;
        }
        return false;
    }

    /**
     * If registered, invoked when {@code player} enters this area or logs in.
     *
     * @param player The player.
     */
    public void enter(Player player) {

    }

    /**
     * If registered, invoked when {@code player} exits this area or logs out.
     *
     * @param player The player.
     */
    public void exit(Player player) {

    }

    /**
     * If registered, invoked when {@code player} moves within this area.
     *
     * @param player The player.
     */
    public void move(Player player) {

    }

    /**
     * Determines if this area contains {@code position}. Runs in O(1) time.
     */
    public boolean contains(Position position) {
        return position.getX() >= southWestX &&
                position.getX() <= northEastX &&
                position.getY() >= southWestY &&
                position.getY() <= northEastY;
    }

    /**
     * Determines if this area contains {@code entity}. Runs in O(1) time.
     */
    public boolean contains(Entity entity) {
        return contains(entity.getPosition());
    }

    /**
     * Returns a random position from this area.
     */
    public Position random() {
        int randomX = RandomUtils.exclusive(width()) + southWestX;
        int randomY = RandomUtils.exclusive(length()) + southWestY;
        return new Position(randomX, randomY);
    }

    /**
     * Returns the center position of this area.
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

    /**
     * Returns the size of this area on a single plane (length * width).
     */
    public int size() {
        return length() * width();
    }
}