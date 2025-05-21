package io.luna.game.task;

import io.luna.game.GameService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ConcurrentModificationException;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

/**
 * A model representing a cyclic unit of work carried out strictly on the game thread.
 * <p>
 * Tasks are the primary mechanism for scheduling delayed or periodic actions in Luna.
 * They are executed on the game thread at regular intervals determined by their delay.
 * <p>
 * Tasks have a lifecycle managed by {@link TaskState}:
 * <ul>
 *   <li>Creation - Task is instantiated with a delay and is in the {@link TaskState#IDLE} state</li>
 *   <li>Scheduling - Task is submitted to a {@link TaskManager} and transitions to {@link TaskState#RUNNING}</li>
 *   <li>Execution - Task's {@link #execute()} method is called when its delay counter reaches the specified delay</li>
 *   <li>Cancellation - Task can be cancelled at any time, transitioning to {@link TaskState#CANCELLED}</li>
 * </ul>
 * <p>
 * Example usage:
 * <pre>
 * // Create a task that runs every 5 ticks
 * Task myTask = new Task(5) {
 *     protected void execute() {
 *         // Do something here
 *     }
 * };
 *
 * // Schedule the task with a TaskManager
 * taskManager.schedule(myTask);
 *
 * // Optionally attach data to the task
 * myTask.setKey(someObject);
 *
 * // Cancel the task when no longer needed
 * myTask.cancel();
 * </pre>
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
     * When true, the {@link #execute()} method will be called immediately upon scheduling,
     * before waiting for the first delay cycle.
     */
    private final boolean instant;

    /**
     * The cyclic delay.
     * <p>
     * Represents the number of game ticks that must pass between executions of this task.
     * A tick is typically 600ms.
     */
    private int delay;

    /**
     * Tracks the current lifecycle state of this task. See {@link TaskState} for details.
     */
    private TaskState state = TaskState.IDLE;

    /**
     * The execution counter. This task is ran when it reaches the delay.
     * <p>
     * Incremented each tick while the task is active. When it reaches the delay value,
     * the task is executed and the counter is reset.
     */
    private int delayCounter;

    /**
     * The amount of times {@link #execute()} has been called since the creation of the task.
     */
    private int executionCounter;

    /**
     * An optional object that can be attached to this task for reference. Useful for
     * associating tasks with game entities or for task filtering.
     */
    private Optional<Object> key = Optional.empty();

    /**
     * Creates a new {@link Task}.
     *
     * @param instant If execution happens instantly.
     * @param delay   The cyclic delay.
     * @throws IllegalArgumentException If the delay is not positive.
     */
    public Task(boolean instant, int delay) {
        checkArgument(delay > 0);
        this.instant = instant;
        this.delay = delay;
    }

    /**
     * Creates a new {@link Task} that doesn't execute instantly.
     *
     * @param delay The cyclic delay.
     * @throws IllegalArgumentException If the delay is not positive.
     */
    public Task(int delay) {
        this(false, delay);
    }

    /**
     * A function representing the unit of work that will be carried out.
     * <p>
     * This method is called when the task is ready to execute (when the delay counter
     * reaches the specified delay). Subclasses must implement this method to define
     * the task's behavior.
     * <p>
     * Any exceptions thrown by this method will be caught and passed to {@link #onException(Exception)}.
     */
    protected abstract void execute();

    /**
     * Increments the timer and determines if this task is ready to be ran.
     * <p>
     * This method is called by the {@link TaskManager} on each game tick to determine
     * if the task should be executed.
     *
     * @return {@code true} if this task is ready to be ran.
     */
    final boolean isReady() {
        if (++delayCounter >= delay && state == TaskState.RUNNING) {
            delayCounter = 0;
            return true;
        }
        return false;
    }

    /**
     * Runs this task once. Forwards any errors to {@link #onException(Exception)}.
     * <p>
     * This method is called by the {@link TaskManager} when the task is ready to execute.
     * It handles exception management and increments the execution counter.
     */
    final void runTask() {
        try {
            execute();
        } catch (Exception e) {
            onException(e);
        } finally {
            executionCounter++;
        }
    }

    /**
     * Cancels all subsequent executions, and fires {@link #onCancel()}. Does nothing if already
     * cancelled.
     * <p>
     * Once cancelled, a task cannot be reused. Create a new task instance if you need
     * similar functionality again.
     */
    public final void cancel() {
        if (state != TaskState.CANCELLED) {
            onCancel();
            executionCounter = 0;
            state = TaskState.CANCELLED;
        }
    }

    /**
     * A function executed every tick while this task is active.
     * <p>
     * This method is called by the {@link TaskManager} on each game tick before checking
     * if the task is ready to execute. It can be used for continuous processing that needs
     * to happen regardless of the task's execution cycle.
     * <p>
     * <strong>Tasks should not be scheduled within this method or a {@link ConcurrentModificationException} will be
     * thrown.</strong> To get around this, use {@link GameService#sync(Runnable)}.
     *
     * @throws ConcurrentModificationException If a task is scheduled within this function.
     */
    protected void onProcess() throws ConcurrentModificationException {

    }

    /**
     * A function executed when this task is scheduled.
     * <p>
     * This method is called by the {@link TaskManager} when the task is first scheduled.
     * It can be used to perform initialization logic or to determine if the task should
     * actually be scheduled.
     *
     * @return {@code true} if this task should still be scheduled.
     */
    protected boolean onSchedule() {
        return true;
    }

    /**
     * A function executed when this task is cancelled.
     * <p>
     * This method is called by {@link #cancel()} when the task is cancelled.
     */
    protected void onCancel() {

    }

    /**
     * A function executed on thrown exceptions. The default behaviour is to log the exception.
     * <p>
     * This method is called when an exception is thrown from {@link #execute()}.
     * Subclasses can override this method to provide custom exception handling.
     *
     * @param failure The exception that was thrown.
     */
    protected void onException(Exception failure) {
        logger.catching(failure);
    }

    /**
     * Sets a new key.
     *
     * @param newKey The key to attach.
     * @return This task instance, for chaining.
     */
    public Task setKey(Object newKey) {
        checkState(key.isEmpty(), "Task already has an attachment.");
        key = Optional.ofNullable(newKey);
        return this;
    }

    /**
     * @return {@code true} if execution happens instantly.
     */
    public boolean isInstant() {
        return instant;
    }

    /**
     * @return The cyclic delay.
     */
    public int getDelay() {
        return delay;
    }

    /**
     * Sets the cyclic delay.
     *
     * @param delay The new value to set.
     */
    public void setDelay(int delay) {
        this.delay = delay;
    }

    /**
     * @return The state.
     */
    public TaskState getState() {
        return state;
    }

    /**
     * Sets the state.
     *
     * @param state The new value to set.
     */
    void setState(TaskState state) {
        this.state = state;
    }

    /**
     * @return The amount of times {@link #execute()} has been called since the creation of the task.
     */
    public int getExecutionCounter() {
        return executionCounter;
    }

    /**
     * @return An optional attachment.
     */
    public Optional<Object> getAttachment() {
        return key;
    }
}
