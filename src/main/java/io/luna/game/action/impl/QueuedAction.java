package io.luna.game.action.impl;

import com.google.common.primitives.Ints;
import io.luna.game.action.Action;
import io.luna.game.action.ActionType;
import io.luna.game.action.TimeSource;
import io.luna.game.model.EntityState;
import io.luna.game.model.mob.Mob;
import io.luna.game.task.Task;

/**
 * An {@link Action} that enforces a cooldown via {@link TimeSource} while allowing exactly one buffered execution
 * to be scheduled during the cooldown window.
 * <p>This differs from {@link ThrottledAction}:
 * <ul>
 *   <li>{@link ThrottledAction}: ignores triggers during cooldown.</li>
 *   <li>{@link QueuedAction}: buffers one trigger and runs it as soon as allowed.</li>
 * </ul>
 * <p>
 * Used for content where players commonly spam the interaction and expect one extra input to be buffered
 * (e.g., alchemy, thieving).
 * <p>
 * The queued execution is managed by {@link TimeSource} (via {@code startQueuedTask}/{@code cancelQueuedTask}),
 * ensuring at most one delayed task exists at a time.
 *
 * @param <T> The mob that owns this action.
 * @author lare96
 */
public abstract class QueuedAction<T extends Mob> extends Action<T> {

    /**
     * Scheduled task that performs the buffered execution once the cooldown expires.
     * <p>
     * The task self-cancels after executing, and clears the {@link TimeSource}'s queued task reference on cancel
     * so future buffered executions may be scheduled.
     */
    private final class QueuedActionTask extends Task {

        /**
         * Creates a new queued execution task.
         *
         * @param delay The remaining ticks until execution should occur.
         */
        public QueuedActionTask(int delay) {
            super(false, delay);
        }

        @Override
        protected void execute() {
            cancel();
            if (mob.getState() == EntityState.ACTIVE) {
                runAndWait();
            }
        }

        @Override
        protected void onCancel() {
            source.resetQueuedTask();
        }
    }

    /**
     * Cooldown duration in ticks between executions.
     */
    private final int delay;

    /**
     * Time source tracking the cooldown state for this action family.
     */
    private final TimeSource source;

    /**
     * Creates a new {@link QueuedAction}.
     *
     * @param mob The mob assigned to this action.
     * @param source The time source.
     * @param delay Cooldown duration (ticks) between executions.
     */
    public QueuedAction(T mob, TimeSource source, int delay) {
        super(mob, ActionType.WEAK);
        this.delay = delay;
        this.source = source;
    }

    /**
     * Executes the action and arms the cooldown window.
     * <p>
     * Also marks the time source as waiting so a single extra trigger can be buffered.
     */
    private void runAndWait() {
        source.reset();
        execute();
        source.setWaiting(true);
    }

    @Override
    public boolean run() {
        if (source.ready(delay)) {
            // Cooldown complete: execute immediately.
            source.cancelQueuedTask();
            runAndWait();
        } else if (source.isWaiting()) {
            // Still cooling down, but we allow buffering one additional execution.
            source.setWaiting(false);

            int remaining = Ints.saturatedCast(delay - source.getDurationTicks());
            if (remaining < 1) {
                source.cancelQueuedTask();
                runAndWait();
            } else {
                source.startQueuedTask(new QueuedActionTask(remaining));
            }
        }
        return true;
    }

    /**
     * Performs the actual action logic. Called at most once per cooldown, plus one buffered execution.
     */
    public abstract void execute();
}
