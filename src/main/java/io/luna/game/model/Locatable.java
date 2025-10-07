package io.luna.game.model;

/**
 * Represents somewhere in the RS2 world that something can be located, at any scale.
 *
 * @author lare96
 */
public interface Locatable {

    /**
     * Determines if {@code position} is located on or within this locatable.
     *
     * @param position The position to check for.
     * @return {@code true} if this position is within this locatable.
     */
    boolean contains(Position position);

    /**
     * Determines if the position occupied by {@code entity} satisfies {@link #contains(Position)}.
     */
    default boolean contains(Entity entity) {
        return contains(entity.position);
    }

    /**
     * @return The {@link Position} of the thing that can be located.
     */
    Position location();
}
