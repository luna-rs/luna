package io.luna.game.action;

/**
 * The execution phases used by an {@link ActionQueue} during a mob's turn.
 * <p>
 * Phases run in declaration order: incoming effects, ordinary actions, interactions, then combat. An action's
 * {@link ActionType} continues to determine interruption and modal rules independently of its phase.
 * <p>
 * After walking, only {@link #INTERACTION} and {@link #COMBAT} are revisited through
 * {@link ActionQueue#processAfterMovement()}. Existing actions retry reach without advancing their counters.
 *
 * @author lare96
 */
public enum ActionPhase {
    /**
     * Incoming damage and other queued effects resolved before ordinary actions and outgoing combat.
     */
    EFFECT,

    /**
     * The default phase for skills, control actions, and other ordinary queued work.
     */
    NORMAL,

    /**
     * Reach-based interactions whose listeners may start combat during the same turn.
     */
    INTERACTION,

    /**
     * Outgoing combat after incoming effects, ordinary actions, and interaction changes have been processed.
     */
    COMBAT
}
