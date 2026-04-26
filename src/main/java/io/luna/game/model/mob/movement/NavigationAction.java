package io.luna.game.model.mob.movement;

import io.luna.game.action.Action;
import io.luna.game.action.ActionType;
import io.luna.game.model.Entity;
import io.luna.game.model.Locatable;
import io.luna.game.model.Position;
import io.luna.game.model.mob.Mob;

import java.util.concurrent.CompletableFuture;

/**
 * An action that processes a {@link NavigationRequest} for a {@link Mob}.
 * <p>
 * This action is responsible for moving a mob toward its requested target, re-pathing when continuous tracking is
 * enabled, cancelling stale pathfinding futures, and completing the request's pending result future when navigation
 * finishes.
 *
 * @author lare96
 */
final class NavigationAction extends Action<Mob> {

    /**
     * The mob processing this navigation action.
     */
    private final Mob mob;

    /**
     * The navigation request being processed.
     */
    private final NavigationRequest request;

    /**
     * The walking navigator used to compute and submit paths.
     */
    private final WalkingNavigator navigator;

    /**
     * Whether the navigation target is a mob.
     */
    private final boolean isTargetMob;

    /**
     * Whether the navigation target is an entity.
     */
    private final boolean isTargetEntity;

    /**
     * The last absolute target position this action attempted to path toward.
     * <p>
     * This is used by continuous requests to detect when a moving target has changed position and requires
     * re-pathing.
     */
    private Position lastPosition;

    /**
     * The currently active pathfinding future.
     * <p>
     * This future is cancelled when a target moves, when the mob reaches the target, or when a newer path request
     * replaces it.
     */
    private CompletableFuture<Void> current;

    /**
     * The final result that will be completed on the navigation request.
     */
    private NavigationResult result;

    /**
     * Whether this action reached the target at least once.
     * <p>
     * Continuous requests may reach the target and keep tracking afterward, so this flag preserves successful reach
     * state even if the action finishes later.
     */
    private boolean reachedOnce;

    /**
     * Creates a new {@link NavigationAction}.
     *
     * @param mob The mob processing the navigation request.
     * @param request The navigation request to process.
     */
    public NavigationAction(Mob mob, NavigationRequest request) {
        super(mob, ActionType.WEAK);
        this.mob = mob;
        this.request = request;
        navigator = mob.getNavigator();
        isTargetMob = request.getTarget() instanceof Mob;
        isTargetEntity = request.getTarget() instanceof Entity;
    }

    @Override
    public boolean run() {
        Position sourcePos = mob.getPosition();
        Locatable target = request.getTarget();
        Position targetPos = target.abs();

        if (sourcePos.computeLongestDistance(targetPos) >= 15 || request.getPending().isCancelled()) {
            result = NavigationResult.DIDNT_REACH;
            return true;
        }

        if (isTargetMob) {
            // Interact with target if a mob.
            mob.interact((Mob) target);
        }

        boolean reached = world.getCollisionManager().reached(mob, target, request.getPolicy());
        boolean targetChanged = !targetPos.equals(lastPosition);

        if (sourcePos.equals(targetPos) && isTargetMob && request.getPolicy().getDistance() > 0) {
            // Step away if occupying same tile and interaction policy isn't equal position.
            boolean stepped = navigator.stepRandom(false);
            if (stepped && current != null) {
                current.cancel(true);
                current = null;
            } else if (!stepped && !request.isContinuous()) {
                result = NavigationResult.DIDNT_REACH;
                return true;
            }
            return false;
        }

        if (reached) {
            // When reached: clear walking queue, cancel pathing, and stop action if tracking isn't required.
            reachedOnce = true;
            mob.getWalking().clear(); // TODO: Verify whether this should always clear the queue.
            if (current != null) {
                current.cancel(true);
                current = null;
            }
            return !request.isContinuous();
        }

        if (current != null) {
            if (current.isCompletedExceptionally()) {
                if (request.isContinuous() && targetChanged) {
                    current = null;
                } else {
                    // Could not compute valid path to target.
                    result = NavigationResult.NO_VALID_PATH;
                    return true;
                }
            } else if (current.isDone()) {
                // Pathing finished without reaching the untracked target.
                if (!request.isContinuous() && mob.getWalking().isEmpty()) {
                    result = NavigationResult.DIDNT_REACH;
                    return true;
                }

                // Continuous request, prepare for re-pathing.
                current = null;
            }
        }

        // Check if we need to re-path because our target moved.
        if (!targetChanged && !mob.getWalking().isEmpty()) {
            return false;
        }

        // Cancel any active asynchronous path before re-pathing a continuous request.
        if (current != null && !current.isDone() && request.isContinuous()) {
            current.cancel(true);
            current = null;
        }

        // Re-path to target if needed.
        if (current == null) {
            current = isTargetEntity ?
                    navigator.walk(navigator.computeOffsetPosition((Entity) target, request.getOffsetDir()),
                            request.getPathfinder(), request.isAsync()) :
                    navigator.walk(target, request.getPathfinder(), request.isAsync());
            lastPosition = targetPos;
        }
        return false;
    }

    @Override
    public void onFinished() {
        mob.interact(null);
        if (reachedOnce) {
            result = NavigationResult.REACHED;
        } else if (result == null) {
            result = NavigationResult.DIDNT_REACH;
        }
        request.getPending().complete(result);
    }
}