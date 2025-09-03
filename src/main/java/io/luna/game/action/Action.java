package io.luna.game.action;

import io.luna.game.model.World;
import io.luna.game.model.mob.Mob;
import io.luna.game.task.Task;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * An abstraction model similar to a {@link Task} that is carried out by a {@link Mob}. Actions also differ from regular
 * {@link Task} types because they follow specific rules about how they're processed. These rules are based on their
 * {@link ActionType}.
 * <p>
 * <p>
 * A mob can perform multiple actions per tick, and there is no limit to how many actions can be processing in the
 * {@link ActionQueue}. Nested actions (submitting actions within actions) are supported, but they will not be processed
 * until the next tick (even if {@link #instant} is {@code true}).
 *
 * @param <T> The type of mob dedicated to this action.
 * @author lare96
 */
public abstract class Action<T extends Mob> {

    /**
     * The logger.
     */
    private static final Logger logger = LogManager.getLogger();

    /**
     * The mob assigned to this action.
     */
    protected final T mob;

    /**
     * The world instance.
     */
    protected final World world;

    /**
     * The action type.
     */
    protected final ActionType actionType;

    /**
     * If the first execution of this action should run instantly.
     */
    private final boolean instant;

    /**
     * The action state.
     */
    private ActionState state;

    /**
     * The delay of this action. 0 means the action will run instantly on its first execution (unless it was submitted
     * within another action).
     */
    private int delay;

    /**
     * The mutable counter, used to time the {@link #delay}. The initial value needs to be
     * {@code -1} due to action processing happening after player input.
     */
    private int counter = -1;

    /**
     * How many executions have occurred. An execution means {@link #run()} was called. This value will return {@code 0}
     * on the first execution.
     */
    private int executions;

    /**
     * Creates a new {@link Action} that will run every {@code delay}.
     *
     * @param mob The mob assigned to this action.
     * @param actionType The action type.
     * @param instant If the first execution of this action should run instantly.
     * @param delay The delay.
     */
    public Action(T mob, ActionType actionType, boolean instant, int delay) {
        checkArgument(delay > 0, "Delay must be above 0!");
        this.mob = mob;
        this.actionType = actionType;
        this.instant = instant;
        this.delay = delay;
        world = mob.getWorld();
    }

    /**
     * Creates a new {@link Action} that runs instantly on its first execution, and every {@code 600}ms afterward.
     *
     * @param mob The mob assigned to this action.
     * @param actionType The action type.
     */
    public Action(T mob, ActionType actionType) {
        this(mob, actionType, true, 1);
    }

    /**
     * Submits this action to the underlying mob's {@link ActionQueue}.
     */
    public final void submit() {
        mob.getActions().submit(this);
    }

    /**
     * Determines if this action is ready to be removed from the queue.
     */
    final boolean isComplete() {

        // Run instantly on first execution if needed.
        if(instant && executions == 0) {
            counter = delay;
        }

        // Run if enough time has passed.
        if (++counter >= delay) {
            try {
                counter = 0;
                return run();
            } catch (Exception e) {
                logger.error("An error occurred while running this action.", e);
                interrupt();
            } finally {
                executions++;
            }
        }
        return false;
    }

    /**
     * Runs this action.
     *
     * @return {@code true} if this action has completed, and should be removed from the queue.
     */
    public abstract boolean run();

    /**
     * Completes this action normally.
     */
    public final void complete() {
        if (state == ActionState.PROCESSING) {
            state = ActionState.COMPLETED;
            onFinished();
        }
    }

    /**
     * Completes this action abnormally. Usually as a result of thrown exceptions or triggers from outside this
     * action.
     */
    public final void interrupt() {
        if (state == ActionState.PROCESSING) {
            state = ActionState.INTERRUPTED;
            onFinished();
        }
    }

    /**
     * Called when this action is submitted.
     */
    public void onSubmit() {

    }

    /**
     * Called when this action is processed by {@link ActionQueue#process()}.
     */
    public void onProcess() {

    }

    /**
     * Called after this action is removed, either by interruption or because of successful completion.
     */
    public void onFinished() {

    }

    /**
     * @return The mob assigned to this action.
     */
    public final T getMob() {
        return mob;
    }

    /**
     * @return The action type.
     */
    public ActionType getActionType() {
        return actionType;
    }

    /**
     * @return The action state.
     */
    public ActionState getState() {
        return state;
    }

    /**
     * Sets the action state.
     */
    void setState(ActionState state) {
        this.state = state;
    }

    /**
     * @return If the first execution of this action should run instantly.
     */
    public boolean isInstant() {
        return instant;
    }

    /**
     * @return The delay of this action. 0 means the action will run instantly on its first execution (unless it was
     * submitted within another action).
     */
    public int getDelay() {
        return delay;
    }

    /**
     * Sets the future delay of this action.
     *
     * @param newDelay The new delay.
     */
    public void setDelay(int newDelay) {
        if (delay != newDelay) {
            checkArgument(newDelay > 0, "Delay must be above 0.");
            this.delay = newDelay;
        }
    }

    /**
     * @return How many executions have occurred. An execution means {@link #run()} was called. This value will
     * return {@code 0} on the first execution.
     */
    public int getExecutions() {
        return executions;
    }
}
