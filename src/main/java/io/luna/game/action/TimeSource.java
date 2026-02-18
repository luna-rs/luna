package io.luna.game.action;

import io.luna.game.TickTimer;
import io.luna.game.action.impl.QueuedAction;
import io.luna.game.action.impl.ThrottledAction;
import io.luna.game.model.World;
import io.luna.game.task.Task;

/**
 * A {@link TickTimer}-backed “cooldown clock” used by actions that need rate limiting.
 * <p>
 * {@link TimeSource} is shared state that tracks elapsed ticks since the last execution of a particular action family.
 * It is primarily used by:
 * <ul>
 *   <li>{@link ThrottledAction}: execute only when {@link #ready(int)} is true; ignore otherwise.</li>
 *   <li>{@link QueuedAction}: execute when ready, but allow buffering exactly one additional execution
 *       during the cooldown window.</li>
 * </ul>
 *
 * <h3>Queued execution support</h3>
 * <p>
 * For {@link QueuedAction}, this class can hold at most one scheduled {@link Task} representing a buffered execution.
 * The helper methods ({@link #startQueuedTask(Task)}, {@link #cancelQueuedTask()},
 * {@link #resetQueuedTask()}) manage that single task reference.
 *
 * @author lare96
 */
public final class TimeSource extends TickTimer {

    /**
     * Whether this time source is currently willing to buffer a single additional execution.
     * <p>
     * Typical flow for {@link QueuedAction}:
     * <ul>
     *   <li>After executing: set waiting to {@code true} to allow one buffered trigger.</li>
     *   <li>When a trigger arrives during cooldown: set waiting to {@code false} and schedule a queued task.</li>
     * </ul>
     */
    private boolean waiting;

    /**
     * The currently scheduled queued execution task, or {@code null} if none is scheduled.
     */
    private Task queuedTask;

    /**
     * Creates a new {@link TimeSource}.
     *
     * @param world The world used to schedule queued tasks and drive tick timing.
     */
    public TimeSource(World world) {
        super(world);
    }

    /**
     * Determines whether an action may execute right now, based on elapsed ticks.
     * <p>
     * This returns {@code true} when either:
     * <ul>
     *   <li>the timer has not started yet, or</li>
     *   <li>{@code duration} ticks have elapsed since the last reset/start.</li>
     * </ul>
     * <p>
     * When this method returns {@code true}, the timer is implicitly {@link #reset() reset} and
     * {@link #start() started} to begin a new cooldown window.
     *
     * @param duration The cooldown duration in ticks.
     * @return {@code true} if execution is allowed now.
     */
    public boolean ready(int duration) {
        if (!isRunning() || getDurationTicks() >= duration) {
            reset().start();
            return true;
        }
        return false;
    }

    /**
     * Schedules a queued execution task and records it as the active queued task.
     *
     * @param task The task to schedule.
     */
    public void startQueuedTask(Task task) {
        cancelQueuedTask();
        resetQueuedTask();
        queuedTask = task;
        world.schedule(task);
    }

    /**
     * Clears the tracked queued task reference without cancelling it.
     * <p>
     * This is intended to be called by the queued task itself (e.g., from {@code onCancel()}) after it has been
     * cancelled or completed.
     */
    public void resetQueuedTask() {
        queuedTask = null;
    }

    /**
     * Cancels the currently scheduled queued task (if any).
     * <p>
     * After cancellation, the task should typically call {@link #resetQueuedTask()} from its {@code onCancel()} hook
     * to clear the reference.
     */
    public void cancelQueuedTask() {
        if (queuedTask != null) {
            queuedTask.cancel();
        }
    }

    /**
     * Returns whether this time source is currently waiting to accept a single buffered execution.
     *
     * @return {@code true} if a queued trigger is allowed, otherwise {@code false}.
     */
    public boolean isWaiting() {
        return waiting;
    }

    /**
     * Sets whether this time source is currently waiting to accept a single buffered execution.
     *
     * @param waiting {@code true} to allow one buffered trigger; {@code false} otherwise.
     */
    public void setWaiting(boolean waiting) {
        this.waiting = waiting;
    }
}
