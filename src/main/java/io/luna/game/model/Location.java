package io.luna.game.model;

import io.luna.game.model.mob.Player;

/**
 * Represents somewhere in the RS2 world an {@link Entity} can be located, at any scale.
 *
 * @author lare96
 */
public interface Location {

    /**
     * Determines if {@code position} is located on or within this location.
     *
     * @param position The position to check for.
     * @return {@code true} if this position is within this location.
     */
    boolean contains(Position position);

    /**
     * Determines if the position occupied by {@code entity} satisfies {@link #contains(Position)}.
     */
    default boolean contains(Entity entity) {
        return contains(entity.position);
    }
}
