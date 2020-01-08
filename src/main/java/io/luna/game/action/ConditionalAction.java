package io.luna.game.action;

import io.luna.game.model.mob.Mob;

/**
 * A {@link RepeatingAction} implementation that executes until a condition is {@code false}.
 *
 * @param <T> The mob type.
 */
public abstract class ConditionalAction<T extends Mob> extends RepeatingAction<T> {

    /**
     * Creates a new {@link ConditionalAction}.
     *
     * @param mob The mob.
     * @param instant If it should execute instantly.
     * @param delay The condition check delay.
     */
    public ConditionalAction(T mob, boolean instant, int delay) {
        super(mob, instant, delay);

    }

    /**
     * Executes the action, if {@code false} is returned the action is interrupted.
     *
     * @return {@code false} to interrupt the action, {@code true} to be executing.
     */
    public abstract boolean condition();

    @Override
    public final void repeat() {
        if (!condition()) {
            interrupt();
        }
    }
}
