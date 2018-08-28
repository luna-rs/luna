package io.luna.game.action;

import java.util.Optional;

/**
 * A manager that handles pending and currently processing actions for a single mob.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class ActionSet {

    /**
     * The current action being processed.
     */
    private Optional<Action> currentAction = Optional.empty();

    /**
     * Attempts to submit a new action to this set.
     *
     * @param pending The action to submit.
     */
    public void submit(Action<?> pending) {
        if (currentAction.isPresent()) {
            Action<?> current = currentAction.get();

            if (current.isRunning()) {
                if (current.isEqual(pending)) {
                    current.onEquals(pending);
                    return; // Ignore repeated clicks.
                }
                current.interrupt();
            }
        }
        currentAction = Optional.of(pending);
        pending.init();
    }

    /**
     * Interrupts and discards the current action being processed.
     */
    public void interrupt() {
        if (currentAction.isPresent()) {
            Action current = currentAction.get();
            current.interrupt();

            currentAction = Optional.empty();
        }
    }

    /**
     * Returns the currently processing action.
     */
    public Optional<Action> current() {
        return currentAction;
    }
}
