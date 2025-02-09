package io.luna.game.action;

import io.luna.game.model.Direction;
import io.luna.game.model.Position;
import io.luna.game.model.mob.ModelAnimation;
import io.luna.game.model.mob.ModelAnimation.ModelAnimationBuilder;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.block.ExactMovement;

/**
 * A {@link RepeatingAction} that forces a player to walk to the specified position.
 *
 * @author lare96
 */
public abstract class ExactMovementAction extends RepeatingAction<Player> {

    /**
     * The delay before the movement starts.
     */
    private final int startDelay;

    /**
     * The forced movement route.
     */
    private final ExactMovement movement;

    /**
     * The walking animation.
     */
    private final int walkingAnimationId;

    /**
     * The total duration of the movement.
     */
    private final int totalDuration;

    /**
     * The player's previous model animations.
     */
    private ModelAnimation previousAnimation;

    /**
     * Creates a new {@link ExactMovementAction}.
     *
     * @param player The player.
     * @param startDelay The delay before the movement starts.
     * @param destination The destination.
     * @param walkingAnimationId The walking animation.
     */
    public ExactMovementAction(Player player, int startDelay, Position destination, int walkingAnimationId) {
        this(player, startDelay, destination, walkingAnimationId,
                Direction.between(player.getPosition(), destination));
    }

    /**
     * Creates a new {@link ExactMovementAction}.
     *
     * @param player The player.
     * @param startDelay The delay before the movement starts.
     * @param destination The destination.
     */
    public ExactMovementAction(Player player, int startDelay, Position destination, int walkingAnimationId, Direction direction) {
        super(player, startDelay == 0, 1);
        this.startDelay = startDelay;
        this.walkingAnimationId = walkingAnimationId;

        Position position = mob.getPosition();
        totalDuration = position.computeLongestDistance(destination);
        movement = new ExactMovement(position, destination, 0, (totalDuration * 600) / 30, direction);
    }

    @Override
    public final boolean start() {
        if (onStart()) {
            mob.lock();
            mob.getWalking().clear();
            return true;
        }
        return false;
    }

    @Override
    public final void repeat() {
        if (getExecutions() == startDelay) {
            if (walkingAnimationId != -1) {
                previousAnimation = mob.getModelAnimation();
                mob.setModelAnimation(new ModelAnimationBuilder().setStandingId(walkingAnimationId).setWalkingId(walkingAnimationId).build());
            }
        } else if (getExecutions() == startDelay + 1) {
            mob.exactMove(movement);
            mob.move(movement.getEndPosition());
            onMove();
        } else if (getExecutions() >= totalDuration + 1) {
            onStop();
            if (walkingAnimationId != -1 && previousAnimation != null) {
                mob.setModelAnimation(previousAnimation);
            }
            mob.unlock();
            interrupt();
        }
    }

    @Override
    public final boolean ignoreIf(Action<?> other) {
        return true;
    }

    /**
     * Invoked right before this action starts.
     *
     * @return {@code true} if this action can proceed, {@code false} otherwise.
     */
    public boolean onStart() {
        return true;
    }

    /**
     * Invoked when the forced movement starts.
     */
    public void onMove() {

    }

    /**
     * Invoked when this action is interrupted.
     */
    public void onStop() {

    }
}
