package io.luna.game.action.impl;

import io.luna.game.model.mob.ModelAnimation;
import io.luna.game.model.mob.ModelAnimation.ModelAnimationBuilder;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.block.ExactMovement;
import io.luna.game.model.mob.block.UpdateFlagSet.UpdateFlag;

/**
 * A {@link LockedAction} that forces a player to walk the specified routes; Uses the {@link ExactMovement} update
 * block.
 *
 * @author lare96
 */
public class ExactMovementAction extends LockedAction {

    /**
     * The routes to walk.
     */
    protected final ExactMovement route;

    /**
     * The previous model animations.
     */
    private ModelAnimation previousAnimation;

    /**
     * The current walking animation.
     */
    private int walkingAnimationId = -1;

    /**
     * The execution counter of the current route.
     */
    private int currentExecutions;

    /**
     * The duration of the current route.
     */
    private final int duration;

    /**
     * Creates a new {@link ExactMovementAction}.
     *
     * @param player The player.
     * @param route The route route.
     */
    public ExactMovementAction(Player player, ExactMovement route) {
        super(player);
        this.route = route;
        duration = Math.max(1, route.getStartPosition().computeLongestDistance(route.getEndPosition()));

    }

    /**
     * Creates a new {@link ExactMovementAction} with an initial walking animation.
     *
     * @param player The player.
     * @param route The movement route.
     * @param walkingAnimationId The walking animation.
     */
    public ExactMovementAction(Player player, ExactMovement route, int walkingAnimationId) {
        this(player, route);
        this.walkingAnimationId = walkingAnimationId;
    }

    @Override
    public final void onLock() {
        previousAnimation = mob.getModelAnimation();
        mob.getWalking().clear();
        onStart();
    }

    @Override
    public final boolean run() {
        try {
            if (currentExecutions == 1) {
                if (walkingAnimationId != -1) {
                    previousAnimation = mob.getModelAnimation();
                    mob.setModelAnimation(new ModelAnimationBuilder().setStandingId(walkingAnimationId).
                            setWalkingId(walkingAnimationId).build());
                    mob.getFlags().flag(UpdateFlag.APPEARANCE);
                }
            } else if (currentExecutions == 2) {
                mob.exactMove(route);
                mob.move(route.getEndPosition());
                onMoveStart();
            } else if (currentExecutions >= duration) {
                mob.setModelAnimation(previousAnimation);
                mob.getFlags().flag(UpdateFlag.APPEARANCE);
                onMoveEnd();
                interrupt();
            }
        } finally {
            currentExecutions++;
        }
        return false;
    }

    @Override
    public final void onUnlock() {
        mob.setModelAnimation(previousAnimation);
        mob.getFlags().flag(UpdateFlag.APPEARANCE);
        onStop();
    }

    /**
     * Invoked when this action is submitted.
     */
    public void onStart() {

    }

    /**
     * Invoked when the player starts walking the route.
     */
    public void onMoveStart() {

    }

    /**
     * Invoked when the player arrives at the route's destination.
     */
    public void onMoveEnd() {

    }

    /**
     * Invoked when this action is interrupted.
     */
    public void onStop() {

    }
}
