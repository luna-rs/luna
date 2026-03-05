package io.luna.game.task;

import io.luna.game.action.Action;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Optional;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

/**
 * A model representing a cyclic unit of work carried out strictly on the game thread.
 * <p>
 * Tasks are the primary mechanism for scheduling global delayed or periodic actions in Luna. A {@link TaskManager} advances
 * all active tasks once per game tick and invokes {@link #execute()} whenever the task's delay cycle elapses.
 * <p>
 * For player-bound cyclic units of work, please consider {@link Action} instead.
 *
 * <h2>Lifecycle</h2>
 * Tasks are tracked by {@link TaskState} and typically move through these states:
 * <ul>
 *   <li><b>Idle</b> - Created or restarted but not currently running ({@link TaskState#IDLE}).</li>
 *   <li><b>Running</b> - Actively scheduled and eligible to execute ({@link TaskState#RUNNING}).</li>
 *   <li><b>Cancelled</b> - Permanently stopped; cannot be reused unless {@link #restart()} is called
 *   ({@link TaskState#CANCELLED}).</li>
 * </ul>
 *
 * <h2>Execution model</h2>
 * <ul>
 *   <li>{@link #onSchedule()} is called when the task is first scheduled. Returning {@code false}
 *       prevents scheduling.</li>
 *   <li>{@link #onProcess()} is called every tick while the task is active (before readiness checks).</li>
 *   <li>{@link #execute()} is called when the internal delay counter reaches {@link #delay} while the task is
 *       {@link TaskState#RUNNING}.</li>
 *   <li>{@link #cancel()} stops future executions and calls {@link #onCancel()} exactly once.</li>
 *   <li>{@link #restart()} resets counters and returns the task to {@link TaskState#IDLE}. This does not
 *       reschedule the task; it only resets internal state so it can be scheduled again.</li>
 * </ul>
 *
 * @author lare96
 */
public abstract class Task {

    /**
     * The asynchronous logger.
     */
    private static final Logger logger = LogManager.getLogger();

    /**
     * If execution happens instantly.
     * <p>
     * When true, the scheduler may invoke {@link #execute()} immediately upon scheduling, before waiting for the
     * first delay cycle.
     */
    private final boolean instant;

    /**
     * The cyclic delay in game ticks.
     * <p>
     * Represents the number of ticks that must pass between {@link #execute()} calls while this task is running.
     */
    private int delay;

    /**
     * Tracks the current lifecycle state of this task.
     */
    private TaskState state = TaskState.IDLE;

    /**
     * The delay counter.
     * <p>
     * Incremented once per tick while the task is active. When it reaches {@link #delay} (and the task is
     * {@link TaskState#RUNNING}), {@link #execute()} becomes eligible to run and the counter is reset.
     */
    private int delayCounter;

    /**
     * The amount of times {@link #execute()} has been called since the last {@link #restart()} or {@link #cancel()}.
     */
    private int executionCounter;

    /**
     * An optional attachment key for associating this task with an owner or tag.
     * <p>
     * This is commonly used for filtering/cancelling groups of tasks tied to a specific entity.
     */
    private Optional<Object> key = Optional.empty();

    /**
     * Creates a new {@link Task}.
     *
     * @param instant If execution happens instantly.
     * @param delay The cyclic delay in ticks (must be positive).
     * @throws IllegalArgumentException If {@code delay <= 0}.
     */
    public Task(boolean instant, int delay) {
        checkArgument(delay > 0, "Delay must be positive.");
        this.instant = instant;
        this.delay = delay;
    }

    /**
     * Creates a new {@link Task} that doesn't execute instantly.
     *
     * @param delay The cyclic delay in ticks (must be positive).
     * @throws IllegalArgumentException If {@code delay <= 0}.
     */
    public Task(int delay) {
        this(false, delay);
    }

    /**
     * Defines the unit of work performed when this task executes.
     * <p>
     * This method is invoked on the game thread when the task is ready to execute (i.e., when its delay cycle
     * elapses while {@link TaskState#RUNNING}).
     * <p>
     * Any exception thrown from this method is forwarded to {@link #onException(Exception)}.
     */
    protected abstract void execute();

    /**
     * Advances the internal delay counter and determines if the task is ready to execute.
     * <p>
     * This method is called by the {@link TaskManager} once per tick.
     *
     * @return {@code true} if the task should execute on this tick.
     */
    final boolean isReady() {
        if (++delayCounter >= delay && state == TaskState.RUNNING) {
            delayCounter = 0;
            return true;
        }
        return false;
    }

    /**
     * Executes this task once and increments {@link #executionCounter}.
     * <p>
     * This method is called by the {@link TaskManager} when {@link #isReady()} returns {@code true}
     * (or immediately on schedule when {@link #instant} is enabled, depending on scheduler behaviour).
     * <p>
     * Exceptions thrown by {@link #execute()} are forwarded to {@link #onException(Exception)}.
     */
    final void runTask() {
        try {
            execute();
        } catch (Exception failure) {
            onException(failure);
        } finally {
            executionCounter++;
        }
    }

    /**
     * Cancels all subsequent executions and fires {@link #onCancel()} once.
     * <p>
     * If the task is already cancelled, this method does nothing.
     * <p>
     * Once cancelled, the task is not reusable unless {@link #restart()} is called.
     */
    public final void cancel() {
        if (state != TaskState.CANCELLED) {
            onCancel();
            executionCounter = 0;
            state = TaskState.CANCELLED;
        }
    }

    /**
     * Resets internal counters and returns this task to the {@link TaskState#IDLE} state.
     * <p>
     * This does not automatically reschedule the task. It only clears runtime counters so the task can be
     * scheduled again by a {@link TaskManager}.
     */
    public final void restart() {
        if (state == TaskState.CANCELLED) {
            state = TaskState.IDLE;
            delayCounter = 0;
            executionCounter = 0;
            onRestart();
        }
    }

    /**
     * A hook executed every tick while this task is active.
     * <p>
     * Called by the {@link TaskManager} prior to readiness checks. Override for continuous per-tick work.
     */
    protected void onProcess() {

    }

    /**
     * A hook executed when this task is scheduled.
     * <p>
     * Returning {@code false} prevents this task from being scheduled.
     *
     * @return {@code true} if this task should be scheduled.
     */
    protected boolean onSchedule() {
        return true;
    }

    /**
     * A hook executed when this task is cancelled.
     * <p>
     * Called exactly once when {@link #cancel()} transitions the task to {@link TaskState#CANCELLED}.
     */
    protected void onCancel() {

    }

    /**
     * A hook executed when this task is restarted.
     * <p>
     * Called exactly once when {@link #restart()} transitions the task back to {@link TaskState#IDLE}.
     */
    protected void onRestart() {

    }

    /**
     * A hook executed when {@link #execute()} throws an exception.
     * <p>
     * The default behaviour is to log the exception.
     *
     * @param failure The exception thrown by {@link #execute()}.
     */
    protected void onException(Exception failure) {
        logger.catching(failure);
    }

    /**
     * Attaches a key to this task.
     * <p>
     * A key can only be set once. It is typically used to associate the task with a specific game entity
     * for filtering/cancellation.
     *
     * @param newKey The key to attach (may be {@code null}).
     * @return This task instance, for chaining.
     * @throws IllegalStateException If a key is already present.
     */
    public Task setKey(Object newKey) {
        checkState(key.isEmpty(), "Task already has an attachment.");
        key = Optional.ofNullable(newKey);
        return this;
    }

    /**
     * @return {@code true} if this task is configured for instant execution on schedule.
     */
    public boolean isInstant() {
        return instant;
    }

    /**
     * Returns the cyclic delay in ticks.
     *
     * @return The delay in ticks.
     */
    public int getDelay() {
        return delay;
    }

    /**
     * Sets the cyclic delay in ticks.
     *
     * @param delay The new delay value in ticks.
     */
    public void setDelay(int delay) {
        this.delay = delay;
    }

    /**
     * Returns the current lifecycle state of this task.
     *
     * @return The current state.
     */
    public TaskState getState() {
        return state;
    }

    /**
     * Sets the lifecycle state of this task.
     * <p>
     * Intended for {@link TaskManager} use.
     *
     * @param state The new state.
     */
    void setState(TaskState state) {
        this.state = state;
    }

    /**
     * Returns the number of times {@link #execute()} has been invoked since the last {@link #restart()}
     * or {@link #cancel()}.
     *
     * @return The execution counter.
     */
    public int getExecutionCounter() {
        return executionCounter;
    }

    /**
     * Returns the optional attachment key for this task.
     *
     * @return The task key.
     */
    public Optional<Object> getKey() {
        return key;
    }
}