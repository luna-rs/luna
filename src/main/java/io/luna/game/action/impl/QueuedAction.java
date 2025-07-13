package io.luna.game.action.impl;

import io.luna.game.action.Action;
import io.luna.game.action.ActionState;
import io.luna.game.action.ActionType;
import io.luna.game.action.TimeSource;
import io.luna.game.model.EntityState;
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
     * @param mob    The mob assigned to this action.
     * @param source The time source.
     * @param delay  The delay of this action. How long (in ticks) the mob must wait before executions of this action.
     */
    public QueuedAction(T mob, TimeSource source, int delay) {
        super(mob, ActionType.WEAK);
        this.delay = delay;
        this.source = source;
    }

    @Override
    public boolean run() {
        if (source.getQueued() != null) {
            // We have a queued action, only keep this action if they match.
            return source.getQueued() != this;
        }
        if (source.isWaiting()) {
            // We've received a throttled action, queue it.
            source.setQueued(this);
            source.setWaiting(false);
            mob.getWalking().clear();
            return false;
        }

        mob.getWalking().clear();
        if (source.ready(delay)) {
            execute();
            world.schedule(new Task(delay) {
                @Override
                protected void execute() {
                    // Execute the queued action if the time source is signaled to.
                    if (mob.getState() != EntityState.INACTIVE &&
                            source.getQueued() != null &&
                            source.getQueued().getState() == ActionState.PROCESSING) {
                        source.getQueued().execute();
                        source.reset();
                    }
                    cancel();
                }

                @Override
                protected void onCancel() {
                    source.setQueued(null);
                    source.setWaiting(false);
                }
            });
            source.setWaiting(true);
        }
        return true;
    }

    /**
     * Executes this action.
     */
    public abstract void execute();
}