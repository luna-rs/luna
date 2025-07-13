package io.luna.game.action.impl;

import io.luna.game.model.Direction;
import io.luna.game.model.Position;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.block.Animation;

/**
 * A {@link LockedAction} implementation that enables a {@link Player} to climb somewhere.
 *
 * @author lare96
 */
public final class ClimbAction extends LockedAction {

    /**
     * The destination.
     */
    private final Position destination;

    /**
     * The direction to face after climbing.
     */
    private final Direction direction;

    /**
     * The message to send while climbing.
     */
    private final String message;

    /**
     * Creates a new {@link ClimbAction}.
     *
     * @param player      The player.
     * @param destination The destination.
     * @param direction   The direction.
     * @param message     The message.
     */
    public ClimbAction(Player player, Position destination, Direction direction, String message) {
        super(player);
        this.destination = destination;
        this.direction = direction;
        this.message = message;
    }

    @Override
    public void onLock() {
        mob.getWalking().clear();
    }

    @Override
    public boolean run() {
        if (getExecutions() == 0) {
            mob.sendMessage(message);
            mob.animation(new Animation(828));
            return false;
        } else if (getExecutions() == 1) {
            mob.move(destination);
            mob.face(direction);
            return false;
        }
        return true;
    }
}
