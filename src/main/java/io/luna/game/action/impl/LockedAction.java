package io.luna.game.action.impl;

import io.luna.game.action.Action;
import io.luna.game.action.ActionType;
import io.luna.game.model.mob.Player;

/**
 * An {@link Action} that locks a {@link Player} for the duration of the action.
 * <p>
 * Locked actions always use {@link ActionType#STRONG}, ensuring they take priority and prevent other conflicting
 * actions from running. The player is locked in {@link #onSubmit()} and unlocked in {@link #onFinished()}
 * (even if the action is cancelled/removed).
 * <p>
 * Override {@link #onLock()} and {@link #onUnlock()} for side effects (interfaces, animations, etc.).
 *
 * @author lare96
 */
public abstract class LockedAction extends Action<Player> {

    /**
     * Creates a locked action with default scheduling behavior.
     *
     * @param player The player.
     */
    public LockedAction(Player player) {
        super(player, ActionType.STRONG);
    }

    /**
     * Creates a locked action with explicit scheduling behavior.
     *
     * @param player The player.
     * @param instant Whether this action executes instantly.
     * @param delay Delay (ticks) between executions.
     */
    public LockedAction(Player player, boolean instant, int delay) {
        super(player, ActionType.STRONG, instant, delay);
    }

    @Override
    public final void onSubmit() {
        mob.lock();
        onLock();
    }

    @Override
    public void onFinished() {
        mob.unlock();
        onUnlock();
    }

    /**
     * Hook invoked immediately after the player is locked.
     */
    public void onLock() {
        /* optional */
    }

    /**
     * Hook invoked immediately after the player is unlocked.
     */
    public void onUnlock() {
        /* optional */
    }
}
