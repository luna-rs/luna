package io.luna.game.action;

import io.luna.game.model.mob.overlay.StandardInterface;

/**
 * Defines how an {@link Action} is prioritized and processed inside an {@link ActionQueue}.
 * <p>
 * Action types control:
 * <ul>
 *   <li><b>Ordering / priority</b> relative to other queued actions</li>
 *   <li><b>Whether weaker actions are removed</b> when stronger actions are present</li>
 *   <li><b>Whether execution is suppressed</b> under certain UI states</li>
 *   <li><b>How interruption APIs behave</b></li>
 * </ul>
 * <p>
 * Conceptually this matches the “queue” model used in OSRS-style action processing, where some actions are
 * disposable (weak), some are exclusive (strong), and some are long-running background loops (soft).
 *
 * @author lare96
 * @see <a href="https://osrs-docs.com/docs/mechanics/queues/">Queues (osrs-docs)</a>
 */
public enum ActionType {

    /**
     * A disposable, low-priority action.
     * <p>
     * {@code WEAK} actions may be removed when a {@link #STRONG} action is queued ahead of them, and can be explicitly
     * cancelled via {@link ActionQueue#interruptWeak()}.
     * <p>
     * Typical uses: most skilling loops, minor item interactions, lightweight repeated actions.
     */
    WEAK,

    /**
     * An exclusive, high-priority action.
     * <p>
     * When a {@code STRONG} action is processed, it removes/cancels any queued {@link #WEAK} actions that would
     * conflict with it. Strong actions also close modal interfaces before running, ensuring UI state does not block
     * their execution.
     * <p>
     * Typical uses: teleporting, dialogue-driven sequences, forced movement, hard locks.
     */
    STRONG,

    /**
     * A standard action that respects modal interface state.
     * <p>
     * {@code NORMAL} actions are skipped (not executed for that cycle) if the player has a {@link StandardInterface}
     * open at the time the queue is processed.
     * <p>
     * Typical uses: actions that should not run while the player is in a modal screen, but do not need the exclusivity
     * semantics of {@link #STRONG}.
     */
    NORMAL,

    /**
     * A persistent action that is intended to keep running until explicitly interrupted.
     * <p>
     * {@code SOFT} actions are not automatically removed by other queue semantics and will continue to run until either:
     * <ul>
     *   <li>{@link ActionQueue#interruptAll()} is called, or</li>
     *   <li>the action implementation completes/stops itself.</li>
     * </ul>
     * <p>
     * Typical uses: long-running background loops, periodic effects, or state machines that should survive most user interactions.
     */
    SOFT,
}
