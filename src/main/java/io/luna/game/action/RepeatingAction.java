package io.luna.game.action;

import io.luna.game.model.EntityState;
import io.luna.game.model.mob.Mob;
import io.luna.game.task.Task;
import io.luna.game.task.TaskState;

/**
 * An {@link Action} implementation that automatically repeats for a specified duration, up until the maximum amount of
 * repetitions.
 *
 * @author lare96 <http://github.com/lare96>
 */
public abstract class RepeatingAction<T extends Mob> extends Action<T> {

    /**
     * The {@link Task} processing this repeating action.
     */
    private final class Worker extends Task {

        /**
         * Creates a new {@link Worker}.
         */
        private Worker() {
            super(instant, delay);
        }

        @Override
        protected boolean onSchedule() {
            mob.getWalking().clear();
            return start();
        }

        @Override
        protected void onCancel() {
            stop();
        }

        @Override
        protected void execute() {
            if (mob.getState() == EntityState.INACTIVE) {
                cancel();
                return;
            }
            repeat();
            if (times > 0 && ++executions == times) {
                actionManager.interrupt();
            }
        }
    }

    /**
     * If this action executes instantly.
     */
    protected final boolean instant;

    /**
     * The delay of this action.
     */
    protected final int delay;

    /**
     * The amount of times this action repeats.
     */
    private final int times;

    /**
     * The task processing this action.
     */
    final Worker worker;

    /**
     * The current repetition counter.
     */
    private int executions;

    /**
     * Creates a new {@link RepeatingAction}.
     *
     * @param mob The mob assigned to this action.
     * @param instant If this action should execute instantly.
     * @param delay The initial and/or subsequent delay.
     * @param times The amount of times to repeat.
     */
    public RepeatingAction(T mob, boolean instant, int delay, int times) {
        super(mob);
        this.instant = instant;
        this.delay = delay;
        this.times = times;
        worker = new Worker();
    }

    public RepeatingAction(T mob, boolean instant, int delay) {
        this(mob, instant, delay, 0);
    }

    @Override
    public final void run() {
        world.schedule(worker);
    }

    /**
     * Interrupts this action by cancelling the {@link Worker}.
     */
    final void cancelWorker() {
        worker.cancel();
    }

    /**
     * Function called when this action is started.
     *
     * @return {@code false} to interrupt the action.
     */
    public abstract boolean start();

    /**
     * Function called every {@code delay} by the {@link Worker}.
     */
    public abstract void repeat();

    /**
     * Determines if this action is equal to {@code other}. This is used instead of {@code equals(Object)} so that
     * the {@code equals(Object) -> hashCode()} contract isn't broken.
     *
     * @param other The other {@link Action} to compare.
     */
    public abstract boolean ignoreIf(Action<?> other);

    /**
     * Function called when this action is stopped (interrupted).
     */
    public void stop() {

    }

    /**
     * @return The current delay of this action.
     */
    public final int getDelay() {
        return worker.getDelay();
    }

    /**
     * Dynamically sets the current delay of this action. Will take effect immediately, and the pending execution time is
     * <strong>not</strong> reset.
     */
    public final void setDelay(int delay) {
        worker.setDelay(delay);
    }

    /**
     * @return {@code true} if this Action has been interrupted, {@code false} otherwise.
     */
    final boolean isInterrupted() {
        return worker.getState() == TaskState.CANCELLED;
    }
}
