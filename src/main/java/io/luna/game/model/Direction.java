package io.luna.game.model;

import io.luna.game.model.mob.WalkingQueue.Step;

/**
 * An enum representing movement directions.
 *
 * @author lare96 <http://github.org/lare96>
 * @author Graham
 */
public enum Direction {
    NONE(-1),
    NORTH_WEST(0),
    NORTH(1),
    NORTH_EAST(2),
    WEST(3),
    EAST(4),
    SOUTH_WEST(5),
    SOUTH(6),
    SOUTH_EAST(7);

    /**
     * The direction identifier.
     */
    private final int id;

    /**
     * Creates a new {@link Direction}.
     *
     * @param id The direction identifier.
     */
    Direction(int id) {
        this.id = id;
    }

    /**
     * Returns the direction between two sets of coordinates.
     *
     * @param currentX The current x coordinate.
     * @param currentY The current y coordinate.
     * @param nextX The next x coordinate.
     * @param nextY The next y coordinate.
     * @return The direction between the current and next coordinates.
     */
    @SuppressWarnings("Duplicates")
    public static Direction between(int currentX, int currentY, int nextX, int nextY) {
        int deltaX = nextX - currentX;
        int deltaY = nextY - currentY;

        if (deltaY == 1) {
            if (deltaX == 1) {
                return NORTH_EAST;
            } else if (deltaX == 0) {
                return NORTH;
            } else if (deltaX == -1) {
                return NORTH_WEST;
            }
        } else if (deltaY == -1) {
            if (deltaX == 1) {
                return SOUTH_EAST;
            } else if (deltaX == 0) {
                return SOUTH;
            } else if (deltaX == -1) {
                return SOUTH_WEST;
            }
        } else if (deltaY == 0) {
            if (deltaX == 1) {
                return EAST;
            } else if (deltaX == 0) {
                return NONE;
            } else if (deltaX == -1) {
                return WEST;
            }
        }
        throw new IllegalArgumentException("difference between coordinates must be [-1, 1].");
    }

    /**
     * Returns the direction between two steps.
     *
     * @param current The current step.
     * @param next The next step.
     * @return The direction between the current and next steps.
     */
    public static Direction between(Step current, Step next) {
        return between(current.getX(), current.getY(), next.getX(), next.getY());
    }

    /**
     * @return The direction identifier.
     */
    public final int getId() {
        return id;
    }
}