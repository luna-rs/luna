package io.luna.game.action;

import io.luna.game.model.EntityState;
import io.luna.game.model.mob.Mob;
import io.luna.game.task.Task;
import io.luna.game.task.TaskState;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * An {@link Action} implementation that automatically repeats for a specified duration.
 *
 * @author lare96
 */
public abstract class RepeatingAction<T extends Mob> extends Action<T> {

    /**
     * The {@link Task} processing this repeating action.
     */
    private final class Worker extends Task {

        /**
         * Creates a new {@link Worker}.
         */
        private Worker(boolean instant, int delay) {
            super(instant, delay);
        }

        @Override
        protected void onProcess() {
            process();
        }

        @Override
        protected boolean onSchedule() {
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
            if (maxCycles > 0 && ++currentCycles >= maxCycles) {
                cancel();
            }
        }
    }

    /**
     * The task processing this action.
     */
    private final Worker worker;

    /**
     * The amount of times this action repeats. Default value is {@code Integer.MAX_VALUE}.
     */
    private int maxCycles = Integer.MAX_VALUE;

    /**
     * The current repetition counter.
     */
    private int currentCycles;

    /**
     * Creates a new {@link RepeatingAction}.
     *
     * @param mob The mob assigned to this action.
     * @param instant If this action should execute instantly.
     * @param delay The initial and/or subsequent delay.
     */
    public RepeatingAction(T mob, boolean instant, int delay) {
        super(mob);
        worker = new Worker(instant, delay);
    }

    /**
     * Creates a new {@link RepeatingAction} that repeats indefinitely at a delay of {@code 1}.
     *
     * @param mob The mob assigned to this action.
     * @param instant If this action should execute instantly.
     */
    public RepeatingAction(T mob, boolean instant) {
        this(mob, instant, 1);
    }

    @Override
    public final void run() {
        world.schedule(worker);
    }

    /**
     * Interrupts this action by cancelling the backing {@link Worker}.
     */
    protected final void interrupt() {
        worker.cancel();
    }

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
     * Function called when this action is started.
     *
     * @return {@code false} to interrupt the action.
     */
    public boolean start() {
        return true;
    }

    /**
     * Function called when this action is stopped (interrupted).
     */
    public void stop() {

    }

    /**
     * Forwards to the underlying task's processing function. Called every tick while this action is active.
     */
    public void process() {

    }

    /**
     * Sets the maximum amount of times this action will repeat. Default value is {@code Integer.MAX_VALUE}.
     */
    public final void setRepeat(int amount) {
        checkArgument(amount > 0, "Amount of repetitions must be above 0.");
        maxCycles = amount;
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
    public final void setDelay(int newDelay) {
        worker.setDelay(newDelay);
    }

    /**
     * @return The amount of times this action has repeated.
     */
    public final int getExecutions() {
        return currentCycles;
    }

    /**
     * @return {@code true} if this Action has been interrupted, {@code false} otherwise.
     */
    final boolean isInterrupted() {
        return worker.getState() == TaskState.CANCELLED;
    }
}
