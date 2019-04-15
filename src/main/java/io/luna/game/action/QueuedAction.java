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
            super(delay);
        }

        @Override
        protected void execute() {
            if (mob.getState() != EntityState.INACTIVE && queuedAction.isPresent()) {
                var action = queuedAction.get();
                action.execute();
                source.reset();
            }
            interrupt();
        }
    }

    /**
     * The duration of this action.
     */
    private final int delay;

    /**
     * The time source.
     */
    private final TimeSource source;

    /**
     * The worker processing this action.
     */
    private final Worker worker;

    /**
     * The queued action.
     */
    private Optional<QueuedAction<?>> queuedAction = Optional.empty();

    /**
     * Creates a new {@link QueuedAction}.
     *
     * @param mob The mob assigned to this action.
     * @param source The time source.
     * @param delay The delay of this action. How long (in ticks) the mob must wait before executions of this action.
     */
    public QueuedAction(T mob, TimeSource source, int delay) {
        super(mob);
        this.delay = delay;
        this.source = source;
        worker = new Worker();
    }

    @Override
    public void run() {
        mob.getWalking().clear();
        if (source.ready(delay)) {
            execute();
            world.schedule(worker);
        } else {
            actionManager.resetQueued();
        }
    }

    /**
     * Executes this action.
     */
    public abstract void execute();

    /**
     * Determines if {@code action} should be queued in the current action.
     *
     * @param action The action.
     * @return {@code true} if {@code action} should be queued.
     */
    public boolean queueIf(QueuedAction<?> action) {
        return source.equals(action.source);
    }

    /**
     * Sets the backing queued action.
     */
    void setQueuedAction(QueuedAction<?> pending) {
        queuedAction = Optional.ofNullable(pending);
    }

    /**
     * Resets the internal queued action.
     */
    final void interrupt() {
        worker.cancel();
        actionManager.resetQueued();
    }
}