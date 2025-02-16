package io.luna.game.action;

import io.luna.game.model.Direction;
import io.luna.game.model.Position;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.block.Animation;

/**
 * A {@link RepeatingAction} implementation that enables a {@link Player} to climb somewhere.
 *
 * @author lare96
 */
public final class ClimbAction extends RepeatingAction<Player> {

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
     * @param player The player.
     * @param destination The destination.
     * @param direction The direction.
     * @param message The message.
     */
    public ClimbAction(Player player, Position destination, Direction direction, String message) {
        super(player, false, 1);
        this.destination = destination;
        this.direction = direction;
        this.message = message;
    }

    @Override
    public boolean start() {
        mob.lock();
        mob.getWalking().clear();
        return true;
    }

    @Override
    public void repeat() {
        if (getExecutions() == 0) {
            mob.sendMessage(message);
            mob.animation(new Animation(828));
        } else if (getExecutions() == 2) {
            mob.move(destination);
            mob.face(direction);
        } else if (getExecutions() == 3) {
            mob.unlock();
            interrupt();
        }
    }

    @Override
    public boolean ignoreIf(Action<?> other) {
        return true;
    }
}
