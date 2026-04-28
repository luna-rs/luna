package io.luna.game.model.mob.movement;

/**
 * Represents the result of a navigation attempt.
 * <p>
 * This is used by movement logic to report whether pathfinding failed, movement completed without reaching the
 * target, or the target was successfully reached.
 *
 * @author lare96
 */
public enum NavigationResult {

    /**
     * No valid path could be found to the requested destination.
     */
    NO_VALID_PATH,

    /**
     * A path was found or movement was attempted, but the target was not reached.
     */
    DIDNT_REACH,

    /**
     * The target was successfully reached.
     */
    REACHED
}