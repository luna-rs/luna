package io.luna.game.action;

import io.luna.game.model.World;
import io.luna.game.model.mobile.MobileEntity;
import io.luna.game.task.Task;

/**
 * An abstraction model representing an Action that can be executed by a {@link MobileEntity}. Actions have their own set of
 * unique traits that make them different from {@link Task}s:
 * <ul>
 *      <li> There can only be <strong>one</strong> Action processing at a time per mob
 *      <li> Pending Actions will replace the currently processing Action unless they're identical, in which case the
 *      pending Action is discarded
 *      <li>An Action must be submitted to an ActionSet, <strong>not</strong> TaskManager
 * </ul>
 * An acceptable analogy for Actions is that they are higher-level Tasks. This is reinforced by the fact that they use
 * Task under-the-hood.
 * <p>
 * All Actions must implement {@link Action#equals(Object)} or an {@link IllegalStateException} will be thrown when trying to
 * submit a pending Action. This is needed so that pending Actions which are equal to processing Actions can be discarded.
 * Actions should never be stored in hash-based collections (HashMap, HashSet, etc) and attempting to do so will result in an
 * {@link UnsupportedOperationException}.
 *
 * @param <T> The mob that this Action is dedicated to.
 * @author lare96 <http://github.org/lare96>
 */
public abstract class Action<T extends MobileEntity> {

    /**
     * A {@link Task} implementation that processes an {@link Action}.
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
     * The {@link MobileEntity} assigned to this action.
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
    private final ActionRunner runner = new ActionRunner();

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
    }

    /**
     * The default implementation will always throw an {@link IllegalStateException}.
     */
    @Override
    public boolean equals(Object obj) {
        throw new IllegalStateException("Action type requires equals(Object) implementation");
    }

    /**
     * Will always throw an {@link UnsupportedOperationException}.
     */
    @Override
    public final int hashCode() {
        /*
         Although this breaks the equals/hashCode contract, this shouldn't be an issue because
         an Action will never be inside of a hash-based collection.
         */
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
}
