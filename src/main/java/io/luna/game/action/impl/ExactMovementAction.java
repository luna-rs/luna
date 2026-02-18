package io.luna.game.action.impl;

import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.block.ExactMovement;
import io.luna.game.model.mob.block.PlayerModelAnimation;
import io.luna.game.model.mob.block.UpdateFlagSet.UpdateFlag;

/**
 * A {@link LockedAction} that moves a {@link Player} along an {@link ExactMovement} route using the
 * {@code ExactMovement} update block.
 * <p>
 * This action:
 * <ul>
 *     <li>Temporarily overrides the player's {@link PlayerModelAnimation} (optional walking animation).</li>
 *     <li>Clears the normal {@code WalkingQueue} so only the exact movement applies.</li>
 *     <li>Sends the {@link ExactMovement} update and teleports the player to the route's destination.</li>
 *     <li>Restores the previous {@link PlayerModelAnimation} when the movement finishes or is interrupted.</li>
 * </ul>
 * <p>
 * The internal {@code duration} controls how long the action remains active, based on the longest distance
 * between the route's start and end positions. This lets callers synchronize things such as camera movement,
 * visual effects, or delays with the movement's lifetime.
 *
 * @author lare96
 */
public class ExactMovementAction extends LockedAction {

    /**
     * The exact movement route to execute.
     */
    protected final ExactMovement route;

    /**
     * The model animation applied to the player before this action started.
     * This is restored when the action finishes or is unlocked.
     */
    private PlayerModelAnimation previousAnimation;

    /**
     * The optional walking animation to apply while the exact movement is in progress.
     * A value of {@code -1} indicates that the current animation should be kept.
     */
    private int walkingAnimationId = -1;

    /**
     * Number of times {@link #run()} has executed for this action instance.
     * Used to drive the internal movement timeline.
     */
    private int currentExecutions;

    /**
     * Number of ticks that this action should remain active before it is considered complete.
     * This is at least {@code 1} and is derived from the longest distance between the route's
     * start and end positions.
     */
    private final int duration;

    /**
     * Creates a new {@link ExactMovementAction} that moves the player along the given route
     * without changing their walking animation.
     *
     * @param player The player to move.
     * @param route The exact movement route describing the start and end positions.
     */
    public ExactMovementAction(Player player, ExactMovement route) {
        super(player);
        this.route = route;
        // Use the longest axis distance as a rough duration for how long the movement should be considered active.
        duration = Math.max(1, route.getStartPosition().computeLongestDistance(route.getEndPosition()));
    }

    /**
     * Creates a new {@link ExactMovementAction} that moves the player along the given route and
     * applies a custom walking animation for the duration of the movement.
     *
     * @param player The player to move.
     * @param route The exact movement route describing the start and end positions.
     * @param walkingAnimationId The animation id to use for both standing and walking while moving.
     */
    public ExactMovementAction(Player player, ExactMovement route, int walkingAnimationId) {
        this(player, route);
        this.walkingAnimationId = walkingAnimationId;
    }

    @Override
    public final void onLock() {
        previousAnimation = mob.getModel();
        mob.getWalking().clear();
        onStart();
    }

    @Override
    public final boolean run() {
        try {
            if (currentExecutions == 1) {
                if (walkingAnimationId != -1) {
                    previousAnimation = mob.getModel();
                    mob.setModel(new PlayerModelAnimation.Builder().
                            setStandingId(walkingAnimationId).
                            setWalkingId(walkingAnimationId).
                            build());
                    mob.getFlags().flag(UpdateFlag.APPEARANCE);
                }
            } else if (currentExecutions == 2) {
                mob.exactMove(route);
                mob.move(route.getEndPosition());
                onMoveStart();
            } else if (currentExecutions >= duration) {
                mob.setModel(previousAnimation);
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
        mob.setModel(previousAnimation);
        mob.getFlags().flag(UpdateFlag.APPEARANCE);
        onStop();
    }

    /**
     * Hook invoked when this action is first submitted and the mob is locked.
     * <p>
     * Subclasses can override this to perform setup logic before the movement begins.
     * The default implementation does nothing.
     */
    public void onStart() {

    }

    /**
     * Hook invoked when the player starts moving along the exact route.
     * <p>
     * This is called after the {@link ExactMovement} update and position change have been applied.
     * The default implementation does nothing.
     */
    public void onMoveStart() {

    }

    /**
     * Hook invoked when the player reaches the destination of the exact movement route, just before the action
     * interrupts itself.
     * <p>
     * The default implementation does nothing.
     */
    public void onMoveEnd() {

    }

    /**
     * Hook invoked when this action is interrupted or finishes and is unlocked.
     * <p>
     * Subclasses can override this to perform clean-up or follow-up logic after the movement and animation have been
     * restored. The default implementation does nothing.
     */
    public void onStop() {

    }
}
