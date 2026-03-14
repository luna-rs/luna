package io.luna.game.model;

import com.google.common.collect.ImmutableList;
import io.luna.util.RandomUtils;

import java.util.Set;

import static com.google.common.base.Preconditions.checkState;

/**
 * Represents a cardinal, diagonal, or stationary movement direction.
 *
 * @author lare96
 * @author Graham
 */
public enum Direction {
    NONE(-1, 0, 0),
    NORTH_WEST(0, -1, 1),
    NORTH(1, 0, 1),
    NORTH_EAST(2, 1, 1),
    WEST(3, -1, 0),
    EAST(4, 1, 0),
    SOUTH_WEST(5, -1, -1),
    SOUTH(6, 0, -1),
    SOUTH_EAST(7, 1, -1);

    /**
     * Ordered directions used when evaluating the NPC view cone.
     * <p>
     * The order is significant, as adjacent entries represent neighboring directions in the cone calculation.
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
     * The four cardinal directions in north-east-south-west order.
     */
    public static final ImmutableList<Direction> NESW = ImmutableList.of(NORTH, EAST, SOUTH, WEST);

    /**
     * The four cardinal directions in west-north-east-south order.
     * <p>
     * This ordering matches the client collision mapping layout.
     */
    public static final ImmutableList<Direction> WNES = ImmutableList.of(WEST, NORTH, EAST, SOUTH);

    /**
     * The four diagonal directions in the ordering used by the client collision mapping.
     */
    public static final ImmutableList<Direction> WNES_DIAGONAL = ImmutableList.of(NORTH_WEST, NORTH_EAST, SOUTH_EAST, SOUTH_WEST);

    /**
     * All defined directions, including {@link #NONE}.
     */
    public static final ImmutableList<Direction> ALL = ImmutableList.copyOf(values());

    /**
     * All defined directions except {@link #NONE}.
     */
    public static final ImmutableList<Direction> ALL_EXCEPT_NONE = ImmutableList.copyOf(values()).stream().
            filter(it -> it != Direction.NONE).collect(ImmutableList.toImmutableList());

    /**
     * Cardinal components for {@link #NORTH_EAST}.
     */
    private static final ImmutableList<Direction> NORTH_EAST_COMPONENTS = ImmutableList.of(NORTH, EAST);

    /**
     * Cardinal components for {@link #NORTH_WEST}.
     */
    private static final ImmutableList<Direction> NORTH_WEST_COMPONENTS = ImmutableList.of(NORTH, WEST);

    /**
     * Cardinal components for {@link #SOUTH_EAST}.
     */
    private static final ImmutableList<Direction> SOUTH_EAST_COMPONENTS = ImmutableList.of(SOUTH, EAST);

    /**
     * Cardinal components for {@link #SOUTH_WEST}.
     */
    private static final ImmutableList<Direction> SOUTH_WEST_COMPONENTS = ImmutableList.of(SOUTH, WEST);

    /**
     * The client-facing direction identifier.
     */
    private final int id;

    /**
     * The x-axis translation applied by this direction.
     */
    private final int translateX;

    /**
     * The y-axis translation applied by this direction.
     */
    private final int translateY;

    /**
     * Creates a new direction.
     *
     * @param id The client-facing direction identifier.
     * @param translateX The x-axis translation for this direction.
     * @param translateY The y-axis translation for this direction.
     */
    Direction(int id, int translateX, int translateY) {
        this.id = id;
        this.translateX = translateX;
        this.translateY = translateY;
    }

    /**
     * Selects a random non-biased movement direction.
     * <p>
     * If {@link #NONE} is initially selected, a second roll is performed against either the cardinal or diagonal
     * direction groups so that a movement direction is always returned.
     *
     * @return A random direction other than {@link #NONE}.
     */
    public static Direction random() {
        Direction selected = RandomUtils.random(ALL);
        if (selected == Direction.NONE) {
            if (RandomUtils.nextBoolean()) {
                return RandomUtils.random(Direction.NESW);
            } else {
                return RandomUtils.random(Direction.WNES_DIAGONAL);
            }
        }
        return selected;
    }

    /**
     * Converts this direction into the forced movement orientation value used by the client.
     * <p>
     * This mapping uses the client's west-north-east-south orientation scheme rather than the direction enum ordinal
     * ordering.
     *
     * @return The forced movement orientation identifier.
     * @throws IllegalStateException If called for {@link #NONE}.
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

    /**
     * @return The x translation.
     */
    public int getTranslateX() {
        return translateX;
    }

    /**
     * @return The y translation.
     */
    public int getTranslateY() {
        return translateY;
    }

    /**
     * Gets all directions visible from the supplied facing direction within the three-direction NPC view cone.
     * <p>
     * The returned set contains the supplied direction and its two adjacent directions in {@link #VIEW_CONE}.
     *
     * @param from The base facing direction.
     * @return The visible directions for that facing direction.
     * @throws IllegalStateException If the supplied direction does not exist in {@link #VIEW_CONE}.
     */
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
     * Gets the two cardinal directions that compose a diagonal direction.
     * <p>
     * For example, {@link #NORTH_EAST} resolves to {@link #NORTH} and {@link #EAST}.
     *
     * @param direction The diagonal direction to decompose.
     * @return The two cardinal component directions.
     * @throws IllegalArgumentException If {@code direction} is not diagonal.
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

    /**
     * Gets the opposite of this direction.
     *
     * @return The opposite direction, or {@link #NONE} if this direction is {@link #NONE}.
     */
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
     * Resolves the direction from one coordinate pair to another.
     * <p>
     * Both coordinate differences must normalize to the range {@code [-1, 1]}. This is intended for adjacent-tile
     * movement and stationary comparisons.
     *
     * @param currentX The starting x coordinate.
     * @param currentY The starting y coordinate.
     * @param nextX The destination x coordinate.
     * @param nextY The destination y coordinate.
     * @return The direction from the starting coordinates to the destination
     * coordinates.
     * @throws IllegalArgumentException If the normalized difference falls outside the valid adjacent-step
     * range.
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
     * Resolves the direction from one position to another.
     *
     * @param current The starting position.
     * @param next The destination position.
     * @return The direction from {@code current} to {@code next}.
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

    /**
     * @return {@code true} if this direction is diagonal, otherwise {@code false}.
     */
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