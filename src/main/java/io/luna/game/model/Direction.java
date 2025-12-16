package io.luna.game.model;

import com.google.common.collect.ImmutableList;
import io.luna.game.model.mob.WalkingQueue.Step;
import io.luna.util.RandomUtils;

import java.util.Set;

import static com.google.common.base.Preconditions.checkState;

/**
 * An enum representing movement directions.
 *
 * @author lare96
 * @author Graham
 */
public enum Direction {
    NONE(-1, new Step(0, 0)),
    NORTH_WEST(0, new Step(-1, 1)),
    NORTH(1, new Step(0, 1)),
    NORTH_EAST(2, new Step(1, 1)),
    WEST(3, new Step(-1, 0)),
    EAST(4, new Step(1, 0)),
    SOUTH_WEST(5, new Step(-1, -1)),
    SOUTH(6, new Step(0, -1)),
    SOUTH_EAST(7, new Step(1, -1));

    /**
     * A list of directions representing all possible directions of the NPC view cone, in order.
     */
    public static final ImmutableList<Direction> VIEW_CONE = ImmutableList.of(
            Direction.NORTH,
            Direction.NORTH_WEST,
            Direction.WEST,
            Direction.SOUTH_WEST,
            Direction.SOUTH,
            Direction.SOUTH_EAST,
            Direction.EAST,
            Direction.NORTH_EAST
    );
    /**
     * An array of directions without any diagonal directions.
     */
    public final static ImmutableList<Direction> NESW = ImmutableList.of(NORTH, EAST, SOUTH, WEST);

    /**
     * An array of directions without any diagonal directions, and one step counter-clockwise, as used by
     * the clients collision mapping.
     */
    public final static ImmutableList<Direction> WNES = ImmutableList.of(WEST, NORTH, EAST, SOUTH);

    /**
     * An array of diagonal directions, and one step counter-clockwise, as used by the clients collision
     * mapping.
     */
    public final static ImmutableList<Direction> WNES_DIAGONAL = ImmutableList.of(NORTH_WEST, NORTH_EAST, SOUTH_EAST, SOUTH_WEST);
    public static final ImmutableList<Direction> ALL = ImmutableList.copyOf(values());


    private static final ImmutableList<Direction> NORTH_EAST_COMPONENTS = ImmutableList.of(NORTH, EAST, NORTH_EAST);
    private static final ImmutableList<Direction> NORTH_WEST_COMPONENTS = ImmutableList.of(NORTH, WEST, NORTH_WEST);
    private static final ImmutableList<Direction> SOUTH_EAST_COMPONENTS = ImmutableList.of(SOUTH, EAST, SOUTH_EAST);
    private static final ImmutableList<Direction> SOUTH_WEST_COMPONENTS = ImmutableList.of(SOUTH, WEST, SOUTH_WEST);


    /**
     * The direction identifier.
     */
    private final int id;
    private final Step translate;

    /**
     * Creates a new {@link Direction}.
     *
     * @param id The direction identifier.
     */
    Direction(int id, Step translate) {
        this.id = id;
        this.translate = translate;
    }

    public static Direction random() {
        Direction selected = RandomUtils.random(ALL);
        if(selected == Direction.NONE) {
            if (RandomUtils.nextBoolean()) {
                return RandomUtils.random(Direction.NESW);
            } else {
                return RandomUtils.random(Direction.WNES_DIAGONAL);
            }
        }
        return selected;
    }

    /**
     * Gets the direction as an integer as used orientation in the client maps (WNES as opposed to NESW).
     *
     * @return The direction as an integer.
     */
    public int toForcedMovementId() {
        switch (this) {
            case NORTH:
            case NORTH_EAST:
            case NORTH_WEST:
                return 0;
            case EAST:
                return 1;
            case SOUTH:
            case SOUTH_EAST:
            case SOUTH_WEST:
                return 2;
            case WEST:
                return 3;
            default:
                throw new IllegalStateException("Only a valid direction can have an orientation value");
        }

    }

    public Step getTranslation() {
        return translate;
    }

    public static Set<Direction> getAllVisible(Direction from) {
        int baseIndex = -1;
        for (int index = 0; index < VIEW_CONE.size(); index++) {
            Direction nextDirection = VIEW_CONE.get(index);
            if (nextDirection == from) {
                baseIndex = index;
            }
        }
        checkState(baseIndex >= 0, "Unexpected, base index could not be found.");
        if (baseIndex == VIEW_CONE.size() - 1) {
            return Set.of(VIEW_CONE.get(0), VIEW_CONE.get(baseIndex), VIEW_CONE.get(baseIndex - 1));
        } else if (baseIndex == 0) {
            return Set.of(VIEW_CONE.get(0), VIEW_CONE.get(1), VIEW_CONE.get(VIEW_CONE.size() - 1));
        } else {
            return Set.of(VIEW_CONE.get(baseIndex), VIEW_CONE.get(baseIndex + 1), VIEW_CONE.get(baseIndex - 1));
        }
    }

    /**
     * Get the 2 directions which make up a diagonal direction (i.e., NORTH and EAST for NORTH_EAST).
     *
     * @param direction The direction to get the components for.
     * @return The components for the given direction.
     */
    public static ImmutableList<Direction> diagonalComponents(Direction direction) {
        switch (direction) {
            case NORTH_EAST:
                return NORTH_EAST_COMPONENTS;
            case NORTH_WEST:
                return NORTH_WEST_COMPONENTS;
            case SOUTH_EAST:
                return SOUTH_EAST_COMPONENTS;
            case SOUTH_WEST:
                return SOUTH_WEST_COMPONENTS;
        }

        throw new IllegalArgumentException("Must provide a diagonal direction.");
    }

    public Direction opposite() {
        switch (this) {
            case NORTH_WEST:
                return Direction.SOUTH_EAST;
            case NORTH:
                return Direction.SOUTH;
            case NORTH_EAST:
                return Direction.SOUTH_WEST;
            case WEST:
                return Direction.EAST;
            case EAST:
                return Direction.WEST;
            case SOUTH_WEST:
                return Direction.NORTH_EAST;
            case SOUTH:
                return Direction.NORTH;
            case SOUTH_EAST:
                return Direction.NORTH_WEST;
            default:
                return Direction.NONE;
        }
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
        int deltaX = Integer.signum(nextX - currentX);
        int deltaY = Integer.signum(nextY - currentY);

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
        throw new IllegalArgumentException("difference between coordinates must be [-1, 1] not [" + deltaX + ", " + deltaY + "].");
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
     * Returns the direction between two steps.
     *
     * @param current The current step.
     * @param next The next step.
     * @return The direction between the current and next steps.
     */
    public static Direction between(Position current, Position next) {
        return between(current.getX(), current.getY(), next.getX(), next.getY());
    }

    /**
     * @return The direction identifier.
     */
    public final int getId() {
        return id;
    }

    public boolean isDiagonal() {
        switch (this) {
            case NORTH_EAST:
            case NORTH_WEST:
            case SOUTH_EAST:
            case SOUTH_WEST:
                return true;
        }
        return false;
    }
}