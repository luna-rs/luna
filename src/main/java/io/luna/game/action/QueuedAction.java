package io.luna.game.action;

import io.luna.game.model.EntityState;
import io.luna.game.model.mob.Mob;
import io.luna.game.task.Task;

import java.util.Optional;

/**
 * An {@link Action} implementation which queues up to {@code 1} additional action. Any subsequent actions submitted
 * replace the old action. This behaviour is required for content such as alchemy and thieving.
 * <p>
 * <p>
 * These actions are executed instantly. The {@code duration} parameter simply refers to how long the player must wait
 * to execute a subsequent action. If this action hasn't been interrupted, once the duration completes the queued
 * action is executed.
 *
 * @author lare96 <http://github.com/lare96>
 */
public abstract class QueuedAction<T extends Mob> extends Action<T> {

    /**
     * A {@link Task} implementation that will execute the queued action.
     */
    private final class Worker extends Task {

        public Worker() {
            super(duration);
        }

        @Override
        protected void execute() {
            if (mob.getState() != EntityState.INACTIVE) {
                queuedAction.ifPresent(QueuedAction::execute);
                actionManager.resetQueued();
            }
            cancel();
        }
    }

    /**
     * The duration of this action.
     */
    private final int duration;

    /**
     * The worker processing this action.
     */
    private final Worker worker;

    /**
     * The queued action.
     */
    protected Optional<QueuedAction<?>> queuedAction = Optional.empty();

    /**
     * Creates a new {@link QueuedAction}.
     *
     * @param mob The mob assigned to this action.
     * @param duration The duration of this action.
     */
    public QueuedAction(T mob, int duration) {
        super(mob);
        this.duration = duration;
        worker = new Worker();
    }

    @Override
    public void run() {
        mob.getWalking().clear();
        execute();
        world.schedule(worker);
    }

    /**
     * Determines if {@code action} should be queued in the current action.
     *
     * @param action The action.
     * @return {@code true} if {@code action} should be queued.
     */
    public abstract boolean queueIf(QueuedAction<?> action);

    /**
     * Executes this action.
     */
    protected abstract void execute();

    /**
     * Sets the backing queued action.
     */
    void setQueuedAction(QueuedAction<?> pending) {
        queuedAction = Optional.of(pending);
    }

    /**
     * Retrieves the backing queued action.
     */
    void resetQueuedAction() {
        // cancel task and clear queued action
        worker.cancel();
        queuedAction = Optional.empty();
    }
}