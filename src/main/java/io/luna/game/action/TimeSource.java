package io.luna.game.action;

import io.luna.game.model.World;
import io.luna.util.TickTimer;

/**
 * A {@link TickTimer} implementation that acts as a source of time for {@link ThrottledAction}s and {@link QueuedAction}s.
 *
 * @author lare96 <http://github.com/lare96>
 */
public final class TimeSource extends TickTimer {

    /**
     * If this time source was checked. This is necessary so the first {@link #ready(int)} check always returns
     * {@code true}.
     */
    private boolean checked;

    /**
     * Creates a new {@link TimeSource}.
     *
     * @param world The world.
     */
    public TimeSource(World world) {
        super(world, 0);
    }

    /**
     * Determines if an action is ready to be executed, based off of if {@code duration} has elapsed. If this function
     * was never previously invoked, then it will always return {@code true}. If this function returns {@code true}, this
     * time source will be implicitly reset.
     *
     * @param duration The elapsed duration to check for.
     * @return {@code true} if an action is ready to be executed.
     */
    boolean ready(int duration) {
        if (!checked || getDurationTicks() >= duration) {
            checked = true;
            reset();
            return true;
        }
        return false;
    }
}