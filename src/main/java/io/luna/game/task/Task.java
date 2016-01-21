package io.luna.game.task;

import java.util.Optional;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * An abstraction model that contains functions that enable units of work to be carried out in cyclic intervals.
 *
 * @author lare96 <http://github.org/lare96>
 */
public abstract class Task {

    /**
     * If this {@code Task} executes upon being submitted.
     */
    private final boolean instant;

    /**
     * The dynamic delay of this {@code Task}.
     */
    private int delay;

    /**
     * If this {@code Task} is currently running.
     */
    private boolean running;

    /**
     * A counter that determines when this {@code Task} is ready to execute.
     */
    private int counter;

    /**
     * An attachment for this {@code Task} instance.
     */
    private Optional<Object> key = Optional.empty();

    /**
     * Creates a new {@link Task}.
     *
     * @param instant If this {@code Task} executes upon being submitted.
     * @param delay The dynamic delay of this {@code Task}.
     */
    public Task(boolean instant, int delay) {
        checkArgument(delay > 0);

        this.instant = instant;
        this.delay = delay;
    }

    /**
     * Creates a new {@link Task} that doesn't execute instantly.
     *
     * @param delay The dynamic delay of this {@code Task}.
     */
    public Task(int delay) {
        this(false, delay);
    }

    /**
     * A function executed when the {@code counter} reaches the {@code delay}.
     */
    protected abstract void execute();

    /**
     * Determines if this {@code Task} is ready to execute.
     *
     * @return {@code true} if this {@code Task} can execute, {@code false} otherwise.
     */
    final boolean canExecute() {
        if (++counter >= delay && running) {
            counter = 0;
            return true;
        }
        return false;
    }

    /**
     * Cancels this {@code Task}. If this {@code Task} is already cancelled, does nothing.
     */
    public final void cancel() {
        if (running) {
            onCancel();
            running = false;
        }
    }

    /**
     * A function executed when this {@code Task} is iterated over by the {@link TaskManager}.
     */
    void onLoop() {

    }

    /**
     * A function executed when this {@code Task} is submitted to the {@link TaskManager}.
     */
    void onSchedule() {

    }

    /**
     * A function executed when this {@code Task} is cancelled.
     */
    void onCancel() {

    }

    /**
     * A function executed when this {@code Task} throws an {@code Exception}.
     *
     * @param e The {@code Exception} thrown by this {@code Task}.
     */
    void onException(Exception e) {

    }

    /**
     * Attaches {@code newKey} to this {@code Task}. The equivalent of doing {@code Optional.ofNullable(newKey)}.
     *
     * @param newKey The new key to attach to this {@code Task}.
     * @return An instance of this {@code Task} for method chaining.
     */
    public Task attach(Object newKey) {
        key = Optional.ofNullable(newKey);
        return this;
    }

    /**
     * @return {@code true} if this {@code Task} executes upon being submitted, {@code false} otherwise.
     */
    public boolean isInstant() {
        return instant;
    }

    /**
     * @return The dynamic delay of this {@code Task}.
     */
    public int getDelay() {
        return delay;
    }

    /**
     * Sets the delay of this {@code Task} to {@code delay}.
     */
    public void setDelay(int delay) {
        this.delay = delay;
    }

    /**
     * @return {@code true} if this {@code Task} is running, {@code false} otherwise.
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * @return The attachment for this {@code Task} instance.
     */
    public Optional<Object> getAttachment() {
        return key;
    }
}
