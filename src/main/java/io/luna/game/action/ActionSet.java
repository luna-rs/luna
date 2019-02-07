package io.luna.game.action;

import java.util.Optional;

/**
 * A manager that handles pending and currently processing actions for a single mob.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class ActionSet {
// TODO rewrite action system to work with queued actions
    /**
     * The current action being processed.
     */
    private Optional<Action> currentAction = Optional.empty();

    /**
     * The currently queued action.
     */
    private Optional<QueuedAction<?>> queuedAction = Optional.empty();

    // TODO queued actions

    /**
     * Attempts to submit a new action to this set.
     *
     * @param pending The action to submit.
     */
    public void submit(Action<?> pending) {
        pending.getMob().onSubmitAction(pending);

        if (currentAction.isPresent()) {
            Action<?> current = currentAction.get();

            if (!current.isInterrupted()) {
                if (current.isEqual(pending)) {
                    current.onEquals(pending);
                    return;
                }
                current.interrupt();
            }
        }
        currentAction = Optional.of(pending);
        pending.init();
    }

    protected void setQueuedAction(QueuedAction<?> action) {
        queuedAction = Optional.of(action);
    }

    protected void fireQueuedAction() {
        queuedAction.ifPresent(QueuedAction::execute);
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
     * @return The currently processing action.
     */
    public Optional<Action> current() {
        return currentAction;
    }

    /**
     * @return The currently queued action.
     */
    public Optional<QueuedAction<?>> queued() {
        return queuedAction;
    }
}
