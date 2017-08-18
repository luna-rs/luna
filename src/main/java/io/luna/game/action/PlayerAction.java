package io.luna.game.action;

import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.WalkingQueue;

/**
 * An {@link Action} implementation that details actions related specifically to players.
 *
 * @author lare96 <http://github.org/lare96>
 */
public abstract class PlayerAction extends Action<Player> {

    /**
     * Creates a new {@link PlayerAction}.
     */
    public PlayerAction(Player player, boolean instant, int delay) {
        super(player, instant, delay);
    }

    @Override
    protected final void onInit() {
        if (canInit()) {
            WalkingQueue walking = mob.getWalkingQueue();
            walking.clear();
        } else {
            interrupt();
        }
    }

    /**
     * Returns whether or not the action can be initialized. Return {@code false} to interrupt the action.
     */
    protected boolean canInit() {
        return true;
    }
}
