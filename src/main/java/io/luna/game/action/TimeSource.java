package io.luna.game.action;

import io.luna.game.action.impl.QueuedAction;
import io.luna.game.action.impl.ThrottledAction;
import io.luna.game.model.World;
import io.luna.game.TickTimer;

/**
 * A {@link TickTimer} implementation that acts as a source of time for {@link ThrottledAction}s and {@link QueuedAction}s.
 *
 * @author lare96
 */
public final class TimeSource extends TickTimer {

    /**
     * If this time source is waiting to queue an action.
     */
    private boolean waiting;

    /**
     * Creates a new {@link TimeSource}.
     *
     * @param world The world.
     */
    public TimeSource(World world) {
        super(world);
    }

    /**
     * Determines if an action is ready to be executed, based off of if {@code duration} has elapsed. If this function
     * was never previously invoked, then it will always return {@code true}. If this function returns {@code true}, this
     * time source will be implicitly reset.
     *
     * @param duration The elapsed duration to check for.
     * @return {@code true} if an action is ready to be executed.
     */
    public boolean ready(int duration) {
        if (!isRunning() || getDurationTicks() >= duration) {
            reset().start();
            return true;
        }
        return false;
    }

    /**
     * @return {@code true} If this time source is waiting to queue an action.
     */
    public boolean isWaiting() {
        return waiting;
    }

    /**
     * Sets if this time source is waiting to queue an action.
     */
    public void setWaiting(boolean waiting) {
        this.waiting = waiting;
    }
}