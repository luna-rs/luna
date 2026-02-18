package io.luna.game.action;

import io.luna.game.model.World;
import io.luna.game.model.mob.Mob;
import io.luna.game.task.Task;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * A tick-driven unit of work executed by a {@link Mob} and managed by an {@link ActionQueue}.
 * <p>
 * Actions are conceptually similar to {@link Task}s, but differ in two key ways:
 * <ul>
 *   <li>They are executed <b>per-mob</b> through {@link ActionQueue}, rather than the global task scheduler.</li>
 *   <li>They obey queue semantics defined by {@link ActionType} (weak/strong/normal/soft rules).</li>
 * </ul>
 *
 * <h3>Scheduling model</h3>
 * <ul>
 *   <li>An action may be {@linkplain #submit() submitted} at any time.</li>
 *   <li>Once submitted, it enters {@link ActionState#PROCESSING} and participates in the queue each tick.</li>
 *   <li>{@link #run()} is invoked when the internal tick counter reaches {@link #delay}.</li>
 *   <li>{@link #run()} returning {@code true} marks the action as complete and removes it from the queue.</li>
 * </ul>
 *
 * <h3>Instant execution</h3>
 * <p>If {@link #instant} is {@code true}, the action attempts to execute on its first eligible cycle.
 * However, actions submitted <em>from within another action</em> (nested submission) are still not executed
 * until a subsequent queue cycle, because {@link ActionQueue} snapshots and processes actions per tick.
 *
 * <h3>Lifecycle hooks</h3>
 * <ul>
 *   <li>{@link #onSubmit()} - called immediately when the action is registered into the queue.</li>
 *   <li>{@link #onProcess()} - called once per tick during the processing stage.</li>
 *   <li>{@link #onFinished()} - called once when the action leaves the queue (completed or interrupted).</li>
 * </ul>
 *
 * @param <T> The mob type dedicated to this action.
 * @author lare96
 */
public abstract class Action<T extends Mob> {

    /**
     * Shared logger for action failures.
     */
    protected static final Logger logger = LogManager.getLogger();

    /**
     * The mob executing this action.
     */
    protected final T mob;

    /**
     * Convenience reference to the mob's world.
     */
    protected final World world;

    /**
     * Queue semantics for this action.
     */
    protected final ActionType actionType;

    /**
     * Whether the first execution should occur immediately (subject to queue timing).
     */
    private final boolean instant;

    /**
     * Current lifecycle state of this action within the {@link ActionQueue}.
     */
    private ActionState state;

    /**
     * Tick delay between executions.
     * <p>
     * This is the number of ticks that must elapse between calls to {@link #run()}.
     */
    private int delay;

    /**
     * Tick counter used to time execution.
     * <p>
     * Initialized to {@code -1} because action processing occurs after player input; this keeps the first cycle
     * timing consistent with "instant" actions.
     */
    private int counter = -1;

    /**
     * Number of times {@link #run()} has been invoked.
     * <p>
     * This value is {@code 0} on the first execution, and increments after each attempted call.
     */
    private int executions;

    /**
     * Creates a new {@link Action}.
     *
     * @param mob The mob assigned to this action.
     * @param actionType The queue semantics for this action.
     * @param instant Whether the first execution should run immediately (subject to queue timing).
     * @param delay Tick delay between executions. Must be {@code > 0}.
     * @throws IllegalArgumentException If {@code delay <= 0}.
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
     * Creates a new {@link Action} that executes immediately on its first eligible cycle and then every tick after.
     *
     * @param mob The mob assigned to this action.
     * @param actionType The queue semantics for this action.
     */
    public Action(T mob, ActionType actionType) {
        this(mob, actionType, true, 1);
    }

    /**
     * Submits this action to the owning mob's {@link ActionQueue}.
     * <p>
     * Submission transitions the action to {@link ActionState#PROCESSING} and invokes {@link #onSubmit()}.
     */
    public final void submit() {
        mob.getActions().submit(this);
    }

    /**
     * Evaluates whether the action should execute on this cycle and whether it has completed.
     * <p>
     * This is called internally by {@link ActionQueue} during the execution stage. It:
     * <ul>
     *   <li>applies {@link #instant} behavior on the first execution,</li>
     *   <li>advances the internal counter,</li>
     *   <li>invokes {@link #run()} when the delay elapses,</li>
     *   <li>handles exceptions by logging and {@link #interrupt() interrupting} the action,</li>
     *   <li>increments {@link #executions} after each attempted execution window.</li>
     * </ul>
     *
     * @return {@code true} if {@link #run()} indicates completion and the action should be removed.
     */
    final boolean isComplete() {

        // Run instantly on first execution if requested.
        if (instant && executions == 0) {
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
     * Performs one execution of this action.
     *
     * @return {@code true} if the action has completed and should be removed from the queue.
     */
    public abstract boolean run();

    /**
     * Completes this action normally.
     * <p>
     * Transitions from {@link ActionState#PROCESSING} to {@link ActionState#COMPLETED} and invokes {@link #onFinished()} once.
     */
    public final void complete() {
        if (state == ActionState.PROCESSING) {
            state = ActionState.COMPLETED;
            onFinished();
        }
    }

    /**
     * Interrupts this action abnormally.
     * <p>
     * Transitions from {@link ActionState#PROCESSING} to {@link ActionState#INTERRUPTED} and invokes {@link #onFinished()}
     * once.
     * <p>
     * Interruptions typically occur due to queue rules (e.g., weak vs strong) or exceptions thrown during execution.
     */
    public final void interrupt() {
        if (state == ActionState.PROCESSING) {
            state = ActionState.INTERRUPTED;
            onFinished();
        }
    }

    /**
     * Hook invoked immediately when the action is submitted to the {@link ActionQueue}.
     * <p>
     * Override to perform setup (clear walking, start animations, close interfaces, etc.).
     */
    public void onSubmit() {
        /* optional */
    }

    /**
     * Hook invoked once per tick during the processing stage of {@link ActionQueue#process()}.
     * <p>
     * Override for per-tick bookkeeping that should occur regardless of whether {@link #run()} executes.
     */
    public void onProcess() {
        /* optional */
    }

    /**
     * Hook invoked once after this action leaves the queue, either via {@link #complete()} or {@link #interrupt()}.
     * <p>
     * Override for cleanup (unlocking, stopping animations, resetting state, etc.).
     */
    public void onFinished() {
        /* optional */
    }

    /**
     * @return The mob assigned to this action.
     */
    public final T getMob() {
        return mob;
    }

    /**
     * @return The {@link ActionType} that controls queue semantics for this action.
     */
    public ActionType getActionType() {
        return actionType;
    }

    /**
     * @return The current {@link ActionState}.
     */
    public ActionState getState() {
        return state;
    }

    /**
     * Sets the current {@link ActionState}.
     *
     * <p>Intended for internal use by {@link ActionQueue}.
     */
    void setState(ActionState state) {
        this.state = state;
    }

    /**
     * @return Whether this action attempts to execute immediately on its first eligible cycle.
     */
    public boolean isInstant() {
        return instant;
    }

    /**
     * @return The tick delay between {@link #run()} executions.
     */
    public int getDelay() {
        return delay;
    }

    /**
     * Updates the execution delay for future cycles.
     *
     * @param newDelay The new tick delay. Must be {@code > 0}.
     * @throws IllegalArgumentException If {@code newDelay <= 0}.
     */
    public void setDelay(int newDelay) {
        if (delay != newDelay) {
            checkArgument(newDelay > 0, "Delay must be above 0.");
            this.delay = newDelay;
        }
    }

    /**
     * Returns how many times {@link #run()} has executed.
     * <p>
     * The first time {@link #run()} is invoked, this returns {@code 0}.
     *
     * @return The execution count.
     */
    public int getExecutions() {
        return executions;
    }
}
