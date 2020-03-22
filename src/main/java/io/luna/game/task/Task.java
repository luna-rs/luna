package io.luna.game.task;

import io.luna.game.service.GameService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ConcurrentModificationException;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

/**
 * A model representing a cyclic unit of work carried out strictly on the game thread.
 *
 * @author lare96 <http://github.org/lare96>
 */
public abstract class Task {

    /**
     * The asynchronous logger.
     */
    private static final Logger logger = LogManager.getLogger();

    /**
     * If execution happens instantly.
     */
    private final boolean instant;

    /**
     * The cyclic delay.
     */
    private int delay;

    /**
     * The state.
     */
    private TaskState state = TaskState.IDLE;

    /**
     * The execution counter. This task is ran when it reaches the delay.
     */
    private int executionCounter;

    /**
     * The attachment.
     */
    private Optional<Object> key = Optional.empty();

    /**
     * Creates a new {@link Task}.
     *
     * @param instant If execution happens instantly.
     * @param delay The cyclic delay.
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
     */
    public Task(int delay) {
        this(false, delay);
    }

    /**
     * A function representing the unit of work that will be carried out.
     */
    protected abstract void execute();

    /**
     * Increments the timer and determines if this task is ready to be ran.
     *
     * @return {@code true} if this task is ready to be ran.
     */
    final boolean isReady() {
        if (++executionCounter >= delay && state == TaskState.RUNNING) {
            executionCounter = 0;
            return true;
        }
        return false;
    }

    /**
     * Runs this task once. Forwards any errors to {@link #onException(Exception)}.
     */
    final void runTask() {
        try {
            execute();
        } catch (Exception e) {
            onException(e);
        }
    }

    /**
     * Cancels all subsequent executions, and fires {@link #onCancel()}. Does nothing if already
     * cancelled.
     */
    public final void cancel() {
        if (state != TaskState.CANCELLED) {
            onCancel();
            state = TaskState.CANCELLED;
        }
    }

    /**
     * A function executed every tick while this task is active.
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
     *
     * @return {@code true} if this task should still be scheduled.
     */
    protected boolean onSchedule() {
        return true;
    }

    /**
     * A function executed when this task is cancelled.
     */
    protected void onCancel() {

    }

    /**
     * A function executed on thrown exceptions. The default behaviour is to log the exception.
     *
     * @param failure The exception that was thrown.
     */
    protected void onException(Exception failure) {
        logger.catching(failure);
    }

    /**
     * Attaches a new key.
     *
     * @param newKey The key to attach.
     * @return This task instance, for chaining.
     */
    public Task attach(Object newKey) {
        checkState(!key.isPresent(), "Task already has an attachment.");
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
     * @return An optional attachment.
     */
    public Optional<Object> getAttachment() {
        return key;
    }
}
