package io.luna.game.action;

/**
 * An enum representing the life-cycle of an {@link Action} within an {@link ActionQueue}.
 *
 * @author lare96
 */
public enum ActionState {

    /**
     * The default state when an {@link Action} is created.
     */
    NEW,

    /**
     * The {@link Action} has been submitted to the {@link ActionQueue} and is undergoing processing.
     */
    PROCESSING,

    /**
     * The {@link Action} has completed normally.
     */
    COMPLETED,

    /**
     * The {@link Action} was interrupted while in a {@link #PROCESSING} state.
     */
    INTERRUPTED
}
