package io.luna.game.action;

import io.luna.game.model.mob.Mob;

/**
 * An {@link Action} implementation that is throttled for a period of time. Throttling restricts how many times an action
 * can be submitted, ignoring actions that are considered equal until the delay is over. Some examples of throttled actions
 * include burying bones and identifying herbs.
 *
 * @param <T> The mob that this Action is dedicated to.
 * @author lare96 <http://github.com/lare96>
 */
public abstract class ThrottledAction<T extends Mob> extends Action<T> {

    /**
     * The time source.
     */
    private final TimeSource source;

    /**
     * The throttle delay.
     */
    private int delay;

    /**
     * Creates a new {@link ThrottledAction}.
     *
     * @param mob The mob assigned to this action.
     * @param source The time source.
     * @param delay The throttle delay, in ticks.
     */
    public ThrottledAction(T mob, TimeSource source, int delay) {
        super(mob);
        this.source = source;
        this.delay = delay;
    }

    @Override
    public void run() {
        mob.getWalking().clear();
        if (source.ready(delay)) {
            execute();
        }
    }

    /**
     * Executes this action.
     */
    public abstract void execute();
}