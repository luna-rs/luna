package io.luna.game.action.impl;

import io.luna.game.model.Position;
import io.luna.game.model.mob.ModelAnimation;
import io.luna.game.model.mob.ModelAnimation.ModelAnimationBuilder;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.block.ExactMovement;
import io.luna.game.model.mob.block.UpdateFlagSet.UpdateFlag;

import java.util.ArrayDeque;
import java.util.Queue;

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
    private final Queue<ExactMovement> routes = new ArrayDeque<>();

    /**
     * The previous model animations.
     */
    private ModelAnimation previousAnimation;

    /**
     * The current walking animation.
     */
    private int walkingAnimationId = -1;

    /**
     * The current movement route.
     */
    private ExactMovement currentRoute;

    /**
     * The execution counter of the current route.
     */
    private int currentExecutions;

    /**
     * The duration of the current route.
     */
    private int currentDuration;

    /**
     * Creates a new {@link ExactMovementAction}.
     *
     * @param player The player.
     */
    public ExactMovementAction(Player player) {
        super(player);
    }

    /**
     * Creates a new {@link ExactMovementAction} with a single movement route.
     *
     * @param player   The player.
     * @param movement The movement route.
     */
    public ExactMovementAction(Player player, ExactMovement movement) {
        this(player);
        addRoute(movement);
    }

    /**
     * Creates a new {@link ExactMovementAction} with a single movement route and an initial walking animation.
     *
     * @param player             The player.
     * @param movement           The movement route.
     * @param walkingAnimationId The walking animation.
     */
    public ExactMovementAction(Player player, ExactMovement movement, int walkingAnimationId) {
        this(player, movement);
        this.walkingAnimationId = walkingAnimationId;
    }

    /**
     * Adds a new movement route to the backing queue.
     *
     * @param movement The movement route.
     */
    public final void addRoute(ExactMovement movement) {
        routes.add(movement);
    }

    @Override
    public final void onLock() {
        previousAnimation = mob.getModelAnimation();
        mob.getWalking().clear();
        onStart();
    }

    @Override
    public final boolean run() {
        if (currentRoute == null) {
            if (routes.isEmpty()) {
                return true;
            }
            currentRoute = routes.poll();

            Position start = currentRoute.getStartPosition();
            Position end = currentRoute.getEndPosition();
            currentDuration = start.computeLongestDistance(end) + 1;
        }

        if (currentExecutions == 1) {
            if (walkingAnimationId != -1) {
                previousAnimation = mob.getModelAnimation();
                mob.setModelAnimation(new ModelAnimationBuilder().setStandingId(walkingAnimationId).
                        setWalkingId(walkingAnimationId).build());
            }
        } else if (currentExecutions == 2) {
            mob.exactMove(currentRoute);
            mob.move(currentRoute.getEndPosition());
            onMoveStart(currentRoute);
        } else if (currentExecutions >= currentDuration) {
            ExactMovement oldRoute = currentRoute;
            currentRoute = null;
            currentExecutions = 0;
            currentDuration = 0;
            onMoveEnd(oldRoute);
        }
        currentExecutions++;
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
     * Invoked when the player starts walking the movement route.
     *
     * @param movement The movement route being walked.
     */
    public void onMoveStart(ExactMovement movement) {

    }

    /**
     * Invoked when the player arrives at the current movement route's destination.
     *
     * @param movement The movement route that was just walked.
     */
    public void onMoveEnd(ExactMovement movement) {

    }

    /**
     * Invoked when this action is interrupted.
     */
    public void onStop() {

    }

    /**
     * Sets the current walking animation.
     */
    public final void setWalkingAnimationId(int walkingAnimationId) {
        this.walkingAnimationId = walkingAnimationId;
    }

    public int getWalkingAnimationId() {
        return walkingAnimationId;
    }
}
