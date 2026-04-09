package io.luna.game.model;

import io.luna.game.model.area.Area;
import io.luna.game.model.chunk.Chunk;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Represents something that occupies a location in the RS2 world.
 * <p>
 * Implementations may model different spatial scales, such as:
 * <ul>
 *     <li>A single tile ({@link Position})</li>
 *     <li>A rectangular area or zone ({@link Area})</li>
 *     <li>A region or chunk ({@link Region}/{@link Chunk})</li>
 * </ul>
 *
 * @author lare96
 */
public interface Locatable {

    /**
     * Determines whether the given absolute {@link Position} lies on or inside this locatable.
     * <p>
     * The exact inclusion rules depend on the implementation. For example:
     * <ul>
     *     <li>A single-tile locatable returns {@code true} when {@code position} matches its tile.</li>
     *     <li>A multi-tile area returns {@code true} when {@code position} is within its bounds.</li>
     *     <li>A region or chunk may treat {@code position} as contained if it falls within that space..</li>
     * </ul>
     *
     * @param position The absolute world position to test.
     * @return {@code true} if {@code position} is considered to be within this locatable.
     */
    boolean contains(Position position);

    /**
     * Determines whether the current position of the given {@link Entity} is contained within this locatable.
     * <p>
     * This is a convenience method that delegates to {@link #contains(Position)} using the entity's
     * current absolute {@link Position}.
     *
     * @param entity The entity whose position should be tested.
     * @return {@code true} if {@code entity}'s position is considered to be within this locatable.
     */
    default boolean contains(Entity entity) {
        return contains(entity.position);
    }

    /**
     * Determines whether this locatable is within a square distance of another locatable.
     * <p>
     * Both positions must be on the same plane. Distance is evaluated using axis-aligned tile deltas rather
     * than Euclidean distance, so this returns {@code true} only if both the X and Y differences are less than
     * or equal to {@code distance}.
     * <p>
     * In other words, this performs a Chebyshev-style range check over world coordinates.
     *
     * @param other The other locatable to compare against.
     * @param distance The maximum allowed tile distance on both axes. Must be non-negative.
     * @return {@code true} if both locatables are on the same plane and within {@code distance} tiles on both
     * the X and Y axes, otherwise {@code false}.
     * @throws IllegalArgumentException If {@code distance} is negative.
     */
    default boolean isWithinDistance(Locatable other, int distance) {
        checkArgument(distance >= 0, "Distance must be non-negative.");
        Position abs = abs();
        Position otherAbs = other.abs();

        if (abs.getZ() != otherAbs.getZ()) {
            return false;
        }
        int deltaX = Math.abs(otherAbs.getX() - abs.getX());
        int deltaY = Math.abs(otherAbs.getY() - abs.getY());
        return deltaX <= distance && deltaY <= distance;
    }

    /**
     * Determines whether this locatable is within standard viewing distance of another locatable.
     * <p>
     * This is a convenience wrapper around {@link #isWithinDistance(Locatable, int)} using {@link Position#VIEWING_DISTANCE}.
     *
     * @param other The other locatable to compare against.
     * @return {@code true} if {@code other} is within standard view distance and on the same plane, otherwise {@code false}.
     */
    default boolean isViewableFrom(Locatable other) {
        return isWithinDistance(other, Position.VIEWING_DISTANCE);
    }

    /**
     * Returns an absolute world {@link Position} that represents this locatable.
     * <p>
     * For multi-tile or abstract locatables (for example, regions or areas), this should return a stable anchor
     * position such as the south-west tile, the center tile, or another agreed-upon reference point in absolute tile
     * coordinates.
     *
     * @return An absolute {@link Position} that anchors this locatable in the world.
     */
    Position abs();

    /**
     * Returns the relative X coordinate for this locatable.
     * <p>
     * This is not guaranteed to be an absolute tile X; it is simply the relative X used by this
     * locatable's coordinate system (for example, region X or local X).
     *
     * @return The relative X coordinate.
     */
    int getX();

    /**
     * Returns the relative Y coordinate for this locatable.
     * <p>
     * This is not guaranteed to be an absolute tile Y; it is simply the relative Y used by this
     * locatable's coordinate system (for example, region Y or local Y).
     *
     * @return The relative Y coordinate.
     */
    int getY();
}
