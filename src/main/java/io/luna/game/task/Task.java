package io.luna.game.task;

import java.util.Optional;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * A model representing a cyclic unit of work.
 *
 * @author lare96 <http://github.org/lare96>
 */
public abstract class Task {

    /**
     * If execution happens instantly upon being scheduled.
     */
    private final boolean instant;

    /**
     * The cyclic delay.
     */
    private int delay;

    /**
     * If registration has taken place.
     */
    private boolean running = true;

    /**
     * A counter that determines when execution should take place.
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
     */
    final boolean canExecute() {
        if (++counter >= delay && running) {
            counter = 0;
            return true;
        }
        return false;
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
     * A function executed when iterated over.
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
     * A function executed on thrown exceptions.
     */
    protected void onException(Exception e) {

    }

    /**
     * Attaches a new key.
     */
    public Task attach(Object newKey) {
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
     */
    public void setDelay(int delay) {
        this.delay = delay;
    }

    /**
     * @return {@code true} if registration has taken place.
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
