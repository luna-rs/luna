package io.luna.game.model.mob.combat;

import io.luna.game.action.Action;
import io.luna.game.action.ActionType;
import io.luna.game.model.Direction;
import io.luna.game.model.EntityType;
import io.luna.game.model.Position;
import io.luna.game.model.collision.CollisionManager;
import io.luna.game.model.mob.Mob;
import io.luna.game.model.mob.Npc;
import io.luna.game.model.mob.interact.InteractionPolicy;

/**
 * An {@link Action} that moves a {@link Mob} toward its current combat target.
 * <p>
 * This action is primarily used for pursuit during combat when the attacker is not yet in a valid interaction
 * position. NPCs compute pursuit one step at a time using local collision checks, while players delegate movement
 * to the normal navigator using the supplied {@link InteractionPolicy}.
 * <p>
 * This action is weak and may be replaced by stronger actions when needed.
 *
 * @author lare96
 */
public final class PursuitAction extends Action<Mob> {

    /**
     * The combat context for the pursuing mob.
     */
    private final CombatContext combat;

    /**
     * The interaction policy used to determine whether the target has been reached.
     */
    private final InteractionPolicy policy;

    /**
     * Creates a new {@link PursuitAction}.
     *
     * @param mob The mob performing the pursuit.
     * @param policy The interaction policy that determines valid target reachability.
     */
    public PursuitAction(Mob mob, InteractionPolicy policy) {
        super(mob, ActionType.WEAK, true, 1);
        this.policy = policy;
        combat = mob.getCombat();
    }

    /**
     * Attempts to take the next pursuit step.
     * <p>
     * If no valid step can be found, this action is interrupted.
     *
     * @return {@code true} if pursuit should stop, otherwise {@code false}.
     */
    private boolean tryStep() {
        Direction nextDirection = computeNextStep();
        if (nextDirection == Direction.NONE) {
            interrupt();
            return true;
        } else {
            mob.getWalking().addStep(nextDirection);
            return false;
        }
    }

    @Override
    public void onSubmit() {
        if (mob instanceof Npc) {
            if (tryStep()) {
                interrupt();
            }
        } else if (combat.getTarget() != null) {
            mob.getNavigator().walkUntilReached(combat.getTarget(), policy, false);
        }
    }
    @Override
    public boolean run() {
        if (combat.getTarget() != null && mob instanceof Npc) {
            return tryStep();
        }
        return true;
    }

    /**
     * Computes the next direction this mob should move in order to pursue its combat target.
     * <p>
     * This method performs simple collision-aware chase logic and returns a single step direction. It first checks
     * whether the target is still valid and reachable on the same plane, then tries to determine whether the mob is
     * already in a valid interaction position. If movement is still needed, it applies a basic pursuit pattern with a
     * special diagonal rule for NPC melee-style contact.
     * <p>
     * A return value of {@link Direction#NONE} indicates that no further movement should be taken.
     *
     * @return The next direction to move in, or {@link Direction#NONE} if no valid step exists.
     */
    private Direction computeNextStep() {
        CollisionManager collision = mob.getWorld().getCollisionManager();
        Mob target = combat.getTarget();

        // Target is not on same plane, or non-existent.
        if (target == null || mob.getZ() != target.getZ()) {
            return Direction.NONE;
        }

        // We're already occupying the same tile, combat hook will move NPC.
        Position position = mob.getPosition();
        Position targetPosition = target.getPosition();
        if (position.equals(targetPosition)) {
            return Direction.NONE;
        }

        // We're already in perfect interaction distance and position.
        Direction nextDirection = Direction.between(position, targetPosition);
        if (!nextDirection.isDiagonal() && collision.reached(position, target, policy)) {
            return Direction.NONE;
        }

        // Special OSRS-like melee rule when diagonal to target: don't step diagonally into contact; prefer x only.
        int dx = nextDirection.getTranslateX();
        int dy = nextDirection.getTranslateY();
        Direction directionX = dx > 0 ? Direction.EAST : dx < 0 ? Direction.WEST : Direction.NONE;
        Direction directionY = dy > 0 ? Direction.NORTH : dy < 0 ? Direction.SOUTH : Direction.NONE;
        if (nextDirection.isDiagonal()) {
            if (collision.traversable(position, EntityType.NPC, directionX, false)) {
                return directionX;
            }
            return Direction.NONE;
        }

        // Regular dumb travelling pattern.
        if (collision.traversable(position, EntityType.NPC, nextDirection)) {
            return nextDirection;
        }
        if (directionX != Direction.NONE && collision.traversable(position, EntityType.NPC, directionX)) {
            return directionX;
        }
        if (directionY != Direction.NONE && position.computeLongestDistance(targetPosition) > 1 &&
                collision.traversable(position, EntityType.NPC, directionY)) {
            return directionY;
        }
        return Direction.NONE;
    }
}