package io.luna.game.action;

import io.luna.game.model.World;
import io.luna.game.model.mobile.MobileEntity;
import io.luna.game.task.Task;

/**
 * An abstraction model representing an Action that can be executed by a {@link MobileEntity}. Actions have their own set of
 * unique traits that make them different from {@link Task}s:
 * <ul>
 *     <li> There can only be <strong>one</strong> Action processing at a time per mob
 *     <li> Pending Actions will replace the currently processing Action
 *     <li>An Action must be submitted to an ActionSet, <strong>not</strong> TaskManager
 * </ul>
 * An acceptable analogy for Actions is that they are higher-level Tasks. This is reinforced by the fact that they use Task
 * under-the-hood.
 *
 * @param <T> The mob that this Action is dedicated to.
 * @author lare96 <http://github.org/lare96>
 */
public abstract class Action<T extends MobileEntity> {

    /**
     * A {@link Task} implementation that processes an Action.
     */
    private final class ActionRunner extends Task {

        /**
         * Creates a new {@link ActionRunner}.
         */
        private ActionRunner() {
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
            call();
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
     * The {@link ActionRunner} that runs this action.
     */
    private final ActionRunner runner;

    /**
     * Creates a new {@link Action}.
     *
     * @param mob The {@link MobileEntity} assigned to this action.
     * @param instant If this action executes instantly.
     * @param delay The delay of this action.
     */
    public Action(T mob, boolean instant, int delay) {
        this.mob = mob;
        this.instant = instant;
        this.delay = delay;
        runner = new ActionRunner();
    }

    /**
     * Will always throw an {@link UnsupportedOperationException}.
     */
    @Override
    public final boolean equals(Object obj) {
        throw new UnsupportedOperationException("equals(Object) is not supported for type Action");
    }

    /**
     * Will always throw an {@link UnsupportedOperationException}.
     */
    @Override
    public final int hashCode() {
        throw new UnsupportedOperationException("hashCode() is not supported for type Action");
    }

    /**
     * Initializes this action by scheduling the {@link ActionRunner}.
     */
    protected final void init() {
        World world = mob.getWorld();
        world.schedule(runner);
    }

    /**
     * Interrupts this action by cancelling the {@link ActionRunner}.
     */
    protected final void interrupt() {
        runner.cancel();
    }

    /**
     * Function invoked when this action initializes.
     */
    protected void onInit() {

    }

    /**
     * Function invoked when this action is interrupted.
     */
    protected void onInterrupt() {

    }

    /**
     * Function called every {@code delay} by the {@link ActionRunner}.
     */
    protected abstract void call();

    /**
     * @return {@code true} if this Action has been interrupted, {@code false} otherwise.
     */
    public final boolean isInterrupted() {
        return !runner.isRunning();
    }
}
