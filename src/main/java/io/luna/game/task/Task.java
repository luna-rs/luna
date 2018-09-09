package io.luna.game.task;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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
    private static final Logger LOGGER = LogManager.getLogger();

    /**
     * If execution happens instantly upon being scheduled.
     */
    private final boolean instant;

    /**
     * The cyclic delay.
     */
    private int delay;

    /**
     * If this task is running.
     */
    private boolean running = true;

    /**
     * An execution counter.
     */
    private int counter;

    /**
     * An optional attachment.
     */
    private Optional<Object> key = Optional.empty();

    /**
     * Creates a new {@link Task}.
     *
     * @param instant If execution happens instantly upon being scheduled.
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
     * Determines if execution should take place.
     *
     * @return {@code true} if this task executes on this tick.
     */
    final boolean canExecute() {
        if (++counter >= delay && running) {
            counter = 0;
            return true;
        }
        return false;
    }

    /**
     * Runs this task once. This should only be called by the {@link TaskManager}.
     */
    final void runTask() {
        try {
            execute();
        } catch (Exception e) {
            onException(e);
        }
    }

    /**
     * Cancels all subsequent executions. Does nothing if already cancelled.
     */
    public final void cancel() {
        if (running) {
            onCancel();
            running = false;
        }
    }

    /**
     * A function executed when this task is iterated over.
     */
    protected void onLoop() {

    }

    /**
     * A function executed on registration.
     */
    protected void onSchedule() {

    }

    /**
     * A function executed on cancellation.
     */
    protected void onCancel() {

    }

    /**
     * A function executed on thrown exceptions. The default behaviour is to log the exception.
     *
     * @param failure The exception that was thrown.
     */
    protected void onException(Exception failure) {
        LOGGER.error(failure);
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
     * @return {@code true} if execution happens instantly upon being scheduled.
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
     * @return {@code true} if this task is running.
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * @return An optional attachment.
     */
    public Optional<Object> getAttachment() {
        return key;
    }
}
