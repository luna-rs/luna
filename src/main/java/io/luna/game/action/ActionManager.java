package io.luna.game.action;

import io.luna.game.model.EntityState;

/**
 * A model that handles registration and processing of actions.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class ActionManager {

    /**
     * The last repeating action processed.
     */
    private RepeatingAction<?> repeating;

    /**
     * The last queued action processed.
     */
    private QueuedAction<?> queued;

    /**
     * Submits a pending action to be ran.
     *
     * @param pending The action.
     */
    public void submit(Action<?> pending) {
        var mob = pending.getMob();
        if (mob.getState() == EntityState.INACTIVE) {
            return;
        }

        mob.onSubmitAction(pending);
        if (pending.isRepeating()) {
            handleRepeating((RepeatingAction<?>) pending);
        } else if (pending.isQueued()) {
            // Stop the repeating action before running.
            interruptRepeating();
            handleQueued((QueuedAction<?>) pending);
        } else if (pending.isThrottled()) {
            // Stop any action before running.
            interrupt();
            pending.run();
        } else {
            throw new IllegalStateException("Invalid action type. Must be a RepeatingAction, QueuedAction, or ThrottledAction.");
        }
    }

    /**
     * Handles processing for repeating actions.
     *
     * @param pending The action.
     */
    private void handleRepeating(RepeatingAction<?> pending) {
        if (repeating != null && !repeating.isInterrupted()) {
            if (repeating.ignoreIf(pending)) {
                // Actions are equal, ignore duplicate.
                return;
            }
            repeating.interrupt();
        }

        // Stop any queued action from running. The action will silently interrupt itself once its duration elapses.
        if (queued != null) {
            queued.setQueuedAction(null);
        }

        // Set the new repeating action and run it.
        repeating = pending;
        repeating.run();
    }

    /**
     * Handles processing for queued actions.
     *
     * @param pending The action.
     */
    private void handleQueued(QueuedAction<?> pending) {
        if (queued != null) {
            if (queued.queueIf(pending)) {
                // Actions are equal, set the queued action.
                queued.setQueuedAction(pending);
                return;
            }
            queued.interrupt();
        }

        // Set the new queued action and run it.
        queued = pending;
        queued.run();
    }

    /**
     * Interrupts any currently processing action.
     */
    public void interrupt() {
        interruptRepeating();
        interruptQueued();
    }

    /**
     * Interrupts {@link #repeating} by stopping the action and discarding it.
     */
    private void interruptRepeating() {
        if (repeating != null) {
            repeating.interrupt();
            repeating = null;
        }
    }

    /**
     * Interrupts {@link #queued} by resetting the queued action.
     */
    private void interruptQueued() {
        if (queued != null) {
            queued.interrupt();
        }
    }

    /**
     * Reset the last queued action processed.
     */
    void resetQueued() {
        queued = null;
    }
}
