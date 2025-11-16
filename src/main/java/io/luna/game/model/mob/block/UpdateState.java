package io.luna.game.model.mob.block;

import io.luna.game.model.mob.Mob;

/**
 * Represents the possible update states for a {@link Mob} during the main synchronization cycle.
 * <p>
 * These states are primarily used by the update task pipeline to determine which type of update mask or
 * synchronization logic should be applied.
 * </p>
 *
 * @author lare96
 */
public enum UpdateState {

    /**
     * Indicates an update being sent for the player’s own mob.
     * <p>
     * This state represents the self-update that occurs every cycle, ensuring the player’s own information remains
     * in sync with their client.
     * </p>
     */
    UPDATE_SELF,

    /**
     * Indicates an update for a mob that is already within the player’s local viewport. These updates handle changes
     * for entities that have already been registered as visible.
     */
    UPDATE_LOCAL,

    /**
     * Indicates an update for a mob that has just entered the player’s local viewport.
     * <p>
     * This state handles full initialization — such as spawning new players or NPCs, sending complete appearance
     * data, and registering them into the client’s local mob list.
     * </p>
     */
    ADD_LOCAL
}
