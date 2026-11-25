package io.luna.game.model.mob.movement;

import io.luna.game.action.Action;
import io.luna.game.action.ActionType;
import io.luna.game.model.Position;
import io.luna.game.model.mob.Mob;
import io.luna.game.model.mob.Player;

/**
 * Tracks a player's previous walking tile for the Follow menu option.
 * Both sides of a mutual follow read tiles from before the current tick's walking began.
 *
 * @author lare96
 */
final class PlayerFollowAction extends Action<Mob> {

    private final NavigationRequest request;
    private final Player target;
    private final WalkingNavigator navigator;
    private final WalkingQueue walking;
    private Position lastQueuedStep;
    private boolean reached;

    PlayerFollowAction(Mob mob, NavigationRequest request) {
        super(mob, ActionType.WEAK);
        this.request = request;
        target = (Player) request.getTarget();
        navigator = mob.getNavigator();
        walking = mob.getWalking();
    }

    @Override
    public void onSubmit() {
        // The client can send a red-click walk to the player's current tile before sending Follow.
        navigator.discardPaths();
        walking.clear();
    }

    @Override
    public boolean run() {
        if (request.getPending().isCancelled()) {
            return true;
        }
        Position source = mob.getPosition();
        if (!mob.isAlive() || !target.isAlive() || !source.isViewable(target.getPosition())) {
            reached = false;
            return true;
        }
        if (!mob.isInteractingWith(target)) {
            mob.interact(target);
        }

        if (mob.isActionLocked() || mob.getStatus().isImmobilized() || walking.isLocked() ||
                target.isPendingPlacement()) {
            walking.cancelPathEndingAt(lastQueuedStep);
            return false;
        }
        Position destination = target.getWalking().getFollowPosition();
        reached |= source.equals(destination);
        lastQueuedStep = walking.queueDirectSteps(destination);
        return false;
    }

    @Override
    public void onFinished() {
        walking.cancelPathEndingAt(lastQueuedStep);
        if (mob.isInteractingWith(target)) {
            mob.interact(null);
        }
        navigator.finish(request);
        request.getPending().complete(reached ? NavigationResult.REACHED : NavigationResult.DIDNT_REACH);
    }
}
