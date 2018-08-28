package io.luna.game.action;

import io.luna.game.model.EntityState;
import io.luna.game.model.World;
import io.luna.game.model.mob.Mob;
import io.luna.game.task.Task;

/**
 * An abstraction model representing a mob-specific task. Actions have their own set of unique traits that make
 * them different from tasks
 * <ul>
 * <li>Actions are mob-specific
 * <li>Each mob can only have one processing Action at a time
 * <li>Actions are automatically interrupted (stopped) when a mob goes inactive
 * <li>An Action is submitted to ActionSet, <strong>not</strong> TaskManager
 * </ul>
 * Another acceptable definition for Actions is that they are tasks specialized for mobs.
 *
 * @param <T> The mob that this Action is dedicated to.
 * @author lare96 <http://github.org/lare96>
 */
public abstract class Action<T extends Mob> {

    /**
     * A {@link Task} implementation that processes an Action.
     */
    private final class ActionTask extends Task {

        /**
         * Creates a new {@link ActionTask}.
         */
        private ActionTask() {
            super(instant, delay);
        }

        @Override
        protected void onSchedule() {
            onInit();
        }

        @Override
        protected void onCancel() {
            onInterrupt();
        }

        @Override
        protected void execute() {
            if (mob.getState() == EntityState.INACTIVE) {
                interrupt();
            } else {
                call();
            }
        }
    }

    /**
     * The mob assigned to this action.
     */
    protected final T mob;

    /**
     * If this action executes instantly.
     */
    protected final boolean instant;

    /**
     * The delay of this action.
     */
    protected final int delay;

    /**
     * The task processing this action.
     */
    private final ActionTask runner;

    /**
     * Creates a new {@link Action}.
     *
     * @param mob The {@link Mob} assigned to this action.
     * @param instant If this action executes instantly.
     * @param delay The delay of this action.
     */
    public Action(T mob, boolean instant, int delay) {
        this.mob = mob;
        this.instant = instant;
        this.delay = delay;
        runner = new ActionTask();
    }

    /**
     * Initializes this action by scheduling the {@link ActionTask}.
     */
    protected final void init() {
        World world = mob.getWorld();
        world.schedule(runner);
    }

    /**
     * Interrupts this action by cancelling the {@link ActionTask}.
     */
    protected final void interrupt() {
        runner.cancel();
    }

    /**
     * Function invoked when this action is scheduled.
     */
    protected void onInit() {

    }

    /**
     * Function invoked when this action is interrupted.
     */
    protected void onInterrupt() {

    }

    /**
     * Function invoked when two Actions are considered equal.
     */
    protected void onEquals(Action<?> other) {

    }

    /**
     * Function called every {@code delay} by the {@link ActionTask}.
     */
    protected abstract void call();

    /**
     * Determines if this action is equal to {@code other}. This is used instead of {@code equals(Object)} so that
     * the {@code equals(Object) -> hashCode()} contract isn't broken.
     */
    protected abstract boolean isEqual(Action<?> other);

    /**
     * @return {@code true} if this Action has been interrupted, {@code false} otherwise.
     */
    public final boolean isRunning() {
        return runner.isRunning();
    }

    /**
     * @return The mob assigned to this action.
     */
    public final T getMob() {
        return mob;
    }
}
