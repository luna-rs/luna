package io.luna.game.action.impl;

import com.google.common.primitives.Ints;
import io.luna.game.action.Action;
import io.luna.game.action.ActionType;
import io.luna.game.action.TimeSource;
import io.luna.game.model.mob.Mob;
import io.luna.game.task.Task;

/**
 * An {@link Action} implementation similar to {@link ThrottledAction}, except it queues up to {@code 1} additional
 * equivalent action instead of throttling the action. This behaviour is required for content such as
 * alchemy and thieving.
 * <p>
 * <p>
 * These actions are executed instantly. The {@link #delay} parameter simply refers to how long the player must wait
 * to execute a subsequent action.
 *
 * @author lare96
 */
public abstract class QueuedAction<T extends Mob> extends Action<T> {

    /**
     * The duration of this action.
     */
    private final int delay;

    /**
     * The time source.
     */
    private final TimeSource source;

    /**
     * Creates a new {@link QueuedAction}.
     *
     * @param mob The mob assigned to this action.
     * @param source The time source.
     * @param delay The delay of this action. How long (in ticks) the mob must wait before executions of this action.
     */
    public QueuedAction(T mob, TimeSource source, int delay) {
        super(mob, ActionType.WEAK);
        this.delay = delay;
        this.source = source;
    }

    private void runAndWait() {
        source.reset();
        execute();
        source.setWaiting(true);
    }

    @Override
    public boolean run() {
        if (source.ready(delay)) {
            // Run normally, time source is ready.
            runAndWait();
        } else if (source.isWaiting()) {
            // We've received a throttled action, queue it.
            source.setWaiting(false);
            int remaining = Ints.saturatedCast(delay - source.getDurationTicks());
            if (remaining < 1) {
                // No remaining ticks, run right away.
                runAndWait();
            } else {
                // Schedule for later.
                world.schedule(new Task(false, remaining) {
                    @Override
                    protected void execute() {
                        runAndWait();
                        cancel();
                    }
                });
            }
        }
        return true;
    }

    /**
     * Executes this action.
     */
    public abstract void execute();
}