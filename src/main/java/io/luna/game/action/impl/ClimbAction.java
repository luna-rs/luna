package io.luna.game.action.impl;

import io.luna.game.model.Direction;
import io.luna.game.model.Position;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.block.Animation;

/**
 * A {@link LockedAction} that performs a simple two-step “climb” sequence for a {@link Player}.
 * <p>
 * This is intended for basic ladders/stairs-style transitions. More complex climbing (agility, obstacle timing,
 * direction-specific animations) should be implemented in specialized actions.
 *
 * @author lare96
 */
public final class ClimbAction extends LockedAction {

    /**
     * The destination tile to move the player to after climbing.
     */
    private final Position destination;

    /**
     * The direction the player should face after arriving at {@link #destination}.
     */
    private final Direction direction;

    /**
     * A message sent to the player when the climb begins.
     */
    private final String message;

    /**
     * Creates a new {@link ClimbAction}.
     *
     * @param player The player performing the climb.
     * @param destination The destination to move to after the climb delay.
     * @param direction The direction to face after moving.
     * @param message The message to send when the climb starts.
     */
    public ClimbAction(Player player, Position destination, Direction direction, String message) {
        super(player);
        this.destination = destination;
        this.direction = direction;
        this.message = message;
    }

    @Override
    public void onLock() {
        // Prevent queued movement from interfering with the climb sequence.
        mob.getWalking().clear();
    }

    @Override
    public boolean run() {
        if (getExecutions() == 0) {
            mob.sendMessage(message);

            // Default climb animation. (Consider choosing based on climb direction/content type.)
            mob.animation(new Animation(828));

            // Continue to the next tick to perform the move.
            return false;

        } else if (getExecutions() == 1) {
            mob.move(destination);
            mob.face(direction);

            // Complete after placing the player.
            return false;
        }

        return true;
    }
}
