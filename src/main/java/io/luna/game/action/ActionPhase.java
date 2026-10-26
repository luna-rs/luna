package io.luna.game.action;

/**
 * Execution order within a mob's turn. This is independent of an action's interruption rules.
 */
public enum ActionPhase {
    /** Incoming damage and other effects that must be resolved before interactions. */
    EFFECT,
    /** Skills, control actions, and other ordinary queued work. */
    NORMAL,
    /** Reach-based interactions, which may establish combat intent. */
    INTERACTION,
    /** Outgoing combat, after incoming effects and interaction changes. */
    COMBAT
}
