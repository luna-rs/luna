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
 * Override {@link #beforeLock()} to reject an attempt before acquiring its lock. Override {@link #onLock()} and
 * {@link #onUnlock()} for side effects (interfaces, animations, etc.). Rejected attempts do not release another
 * action's lock or invoke unlock side effects.
 *
 * @author lare96
 */
public abstract class LockedAction extends Action<Player> {

    /**
     * Whether this submission acquired a lock that must be released when it finishes.
     */
    private boolean acquiredLock;

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
        if (!beforeLock() || isFinished()) {
            complete();
            return;
        }
        mob.lock();
        acquiredLock = true;
        onLock();
    }

    @Override
    public final void onFinished() {
        if (acquiredLock) {
            acquiredLock = false;
            mob.unlock();
            onUnlock();
        }
    }

    /**
     * Validates or interrupts an attempt before this action acquires its lock. Shared content such as random
     * events can therefore run without being blocked by the very action offering them.
     *
     * @return {@code true} to acquire the lock and invoke {@link #onLock()}, or {@code false} to complete immediately.
     */
    public boolean beforeLock() {
        return true;
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
