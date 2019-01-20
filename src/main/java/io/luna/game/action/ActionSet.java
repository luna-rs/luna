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
    private Action<?> currentAction;

    /**
     * Attempts to submit a new action to this set.
     *
     * @param pending The action to submit.
     */
    public void submit(Action<?> pending) {
        pending.getMob().onSubmitAction(pending);

        if (currentAction != null) {
            if (currentAction.isRunning()) {
                if (currentAction.isEqual(pending)) {
                    currentAction.onEquals(pending);
                    return;
                }
    
                currentAction.interrupt();
            }
        }
        
        currentAction = pending;
        pending.init();
    }

    /**
     * Interrupts and discards the current action being processed.
     */
    public void interrupt() {
        if (currentAction != null) {
            currentAction.interrupt();
            currentAction = null;
        }
    }

    /**
     * @return The currently processing action.
     */
    public Optional<Action<?>> current() {
        return Optional.ofNullable(currentAction);
    }
}
