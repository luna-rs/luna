package io.luna.game.action.impl;

import io.luna.game.action.Action;
import io.luna.game.action.ActionQueue;
import io.luna.game.action.ActionType;
import io.luna.game.model.mob.Player;

/**
 * An {@link Action} implementation that locks a {@link Player} until the action is removed from its
 * {@link ActionQueue}. All locked actions have a {@link ActionType#STRONG} action type.
 *
 * @author lare96
 */
public abstract class LockedAction extends Action<Player> {

    public LockedAction(Player player) {
        super(player, ActionType.STRONG);
    }

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

    public void onLock() {

    }

    public void onUnlock() {

    }
}
