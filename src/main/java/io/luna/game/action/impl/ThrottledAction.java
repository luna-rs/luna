package io.luna.game.action.impl;

import io.luna.game.action.Action;
import io.luna.game.action.ActionType;
import io.luna.game.action.TimeSource;
import io.luna.game.model.mob.Mob;

/**
 * An {@link Action} implementation that is throttled for a period of time. Throttling restricts how many times an action
 * can run, ignoring actions from the same {@link TimeSource} until the delay is over. Some examples of throttled actions
 * include burying bones and identifying herbs.
 *
 * @param <T> The mob that this Action is dedicated to.
 * @author lare96 
 */
public abstract class ThrottledAction<T extends Mob> extends Action<T> {

    /**
     * The time source.
     */
    private final TimeSource source;

    /**
     * The throttle delay.
     */
    private final int delay;

    /**
     * Creates a new {@link ThrottledAction}.
     *
     * @param mob The mob assigned to this action.
     * @param source The time source.
     * @param delay The throttle delay, in ticks.
     */
    public ThrottledAction(T mob, TimeSource source, int delay) {
        super(mob, ActionType.WEAK);
        this.source = source;
        this.delay = delay;
    }

    @Override
    public boolean run() {
        mob.getWalking().clear();
        if (source.ready(delay)) {
            execute();
        }
        return true;
    }

    /**
     * Executes this action.
     */
    public abstract void execute();
}