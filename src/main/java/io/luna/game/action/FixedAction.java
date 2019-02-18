package io.luna.game.action;

import io.luna.game.model.mob.Mob;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * An {@link Action} implementation that is stationary and has a strict execution limit.
 *
 * @author lare96 <http://github.com/lare96>
 */
public abstract class FixedAction<T extends Mob> extends Action<T> {

    /**
     * The execution limit.
     */
    private final int executionLimit;

    /**
     * The recorded executions.
     */
    private int executions;

    /**
     * Creates a new {@link FixedAction}.
     *
     * @param mob The mob.
     * @param instant If this action executes instantly.
     * @param delay The delay of this action.
     * @param executionLimit The execution limit.
     */
    public FixedAction(T mob, boolean instant, int delay, int executionLimit) {
        super(mob, instant, delay);
        checkArgument(executionLimit > 0, "Execution limit must be > 0.");
        this.executionLimit = executionLimit;
    }

    @Override
    protected void onInit() {
        if (canExecute()) {
            mob.getWalking().clear();
            initialize();
        } else {
            interrupt();
        }
    }

    @Override
    protected final void call() {
        if (canExecute()) {
            execute();
            if (++executions == executionLimit) {
                onExecutionLimit();
                interrupt();
            }
        }
    }

    /**
     * Executes one iteration of this action and records an execution. This function should never be called directly by
     * members outside of this class.
     */
    protected abstract void execute();

    /**
     * Determines if this action can be started and if an iteration can be executed. This action will be interrupted if
     * this function returns {@code false} in the aforementioned contexts.
     */
    protected abstract boolean canExecute();

    /**
     * Fired when the initial call to {@link #canExecute()} returns {@code true} and this action is started.
     */
    protected void initialize() {

    }

    /**
     * Fired when the execution limit is reached. This action will be interrupted once this function completes.
     */
    protected void onExecutionLimit() {

    }
}