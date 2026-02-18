package io.luna.game.action.impl;

import io.luna.game.action.Action;
import io.luna.game.action.ActionType;
import io.luna.game.action.TimeSource;
import io.luna.game.model.mob.Mob;



public abstract class ThrottledAction<T extends Mob> extends Action<T> {

    /**
     * The time source used to track the throttle window.
     */
    private final TimeSource source;

    /**
     * The throttle delay in ticks.
     */
    private final int delay;

    /**
     * Creates a new {@link ThrottledAction}.
     *
     * @param mob The mob owning this action.
     * @param source The time source used for rate limiting.
     * @param delay The throttle delay in ticks.
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
     * Executes the action when the throttle window allows it.
     */
    public abstract void execute();
}
