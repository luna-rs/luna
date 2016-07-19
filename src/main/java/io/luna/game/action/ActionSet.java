package io.luna.game.action;

import java.util.Optional;

/**
 * A model that manages pending and processing {@link Action}s.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class ActionSet {

    /**
     * The current {@link Action} being processed.
     */
    private Optional<Action> currentAction = Optional.empty();

    /**
     * Attempts to submit a new pending {@link Action} to this set.
     *
     * @param pending The {@code Action} to submit.
     */
    public void submit(Action pending) {
        if (currentAction.isPresent()) {
            Action current = currentAction.get();
            if (current.equals(pending)) {
                return;
            }
            current.interrupt();
        }
        currentAction = Optional.of(pending);
        pending.init();
    }

    /**
     * Interrupts and discards the current {@link Action} being processed.
     */
    public void interrupt() {
        if (currentAction.isPresent()) {
            Action current = currentAction.get();
            current.interrupt();

            currentAction = Optional.empty();
        }
    }
}
