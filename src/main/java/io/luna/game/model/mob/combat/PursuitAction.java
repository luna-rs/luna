package io.luna.game.model.mob.combat;

import io.luna.game.action.Action;
import io.luna.game.action.ActionType;
import io.luna.game.model.Direction;
import io.luna.game.model.EntityType;
import io.luna.game.model.Position;
import io.luna.game.model.collision.CollisionManager;
import io.luna.game.model.mob.Mob;
import io.luna.game.model.mob.Npc;
import io.luna.game.model.mob.combat.state.CombatContext;
import io.luna.game.model.mob.interact.InteractionPolicy;

/**
 * An {@link Action} that moves a {@link Mob} toward its current combat target.
 * <p>
 * This action is used while combat is active but the attacker has not yet reached a valid interaction position.
 * NPCs resolve pursuit manually one step at a time using collision checks, while players delegate pathing to the
 * normal navigator with the supplied {@link InteractionPolicy}.
 * <p>
 * Because this is a weak action, it may be replaced by stronger actions whenever necessary.
 *
 * @author lare96
 */
public final class PursuitAction extends Action<Mob> {

    /**
     * The combat context for the pursuing mob.
     */
    private final CombatContext<?> combat;

    /**
     * The interaction policy that determines when the target is considered reached.
     */
    private final InteractionPolicy policy;

    /**
     * Creates a new {@link PursuitAction}.
     *
     * @param mob The mob performing pursuit.
     * @param policy The interaction policy used to test valid reachability.
     */
    public PursuitAction(Mob mob, InteractionPolicy policy) {
        super(mob, ActionType.WEAK);
        this.policy = policy;
        combat = mob.getCombat();
    }

    /**
     * Attempts to queue the next pursuit step for this mob.
     *
     * @return {@code true} if pursuit should stop, otherwise {@code false}.
     */
    private boolean tryStep() {
        Direction nextDirection = computeNextStep();
        if (nextDirection == Direction.NONE) {
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
                complete();
            }
        } else if (combat.getTarget() != null) {
            mob.getNavigator().walkUntilReached(combat.getTarget(), policy, false);
        }
    }

    @Override
    public boolean run() {
        Mob target = combat.getTarget();
        if (!combat.inCombat() || target == null || !mob.isViewableFrom(target)) {
            return true;
        }
        return !(mob instanceof Npc) || tryStep();
    }

    /**
     * Computes the next single-step movement direction needed to pursue the current combat target.
     * <p>
     * This method performs simple collision-aware chase logic:
     * <ul>
     *     <li>Rejects missing or off-plane targets</li>
     *     <li>Tries to separate overlapping entities</li>
     *     <li>Handles close diagonal adjacency by attempting a component step</li>
     *     <li>Stops if the target is already reachable under the current interaction policy</li>
     *     <li>Tries diagonal movement first, then x-axis, then y-axis</li>
     * </ul>
     * A return value of {@link Direction#NONE} means that no further step should be taken.
     *
     * @return The next direction to move in, or {@link Direction#NONE} if no valid step exists.
     */
    private Direction computeNextStep() {
        CollisionManager collision = mob.getWorld().getCollisionManager();
        Mob target = combat.getTarget();

        // Target does not exist or is on a different plane.
        if (target == null || mob.getZ() != target.getZ()) {
            return Direction.NONE;
        }

        Position position = mob.getPosition();
        Position targetPosition = target.getPosition();

        // Occupying the same tile is invalid for pursuit, so try to move away.
        if (position.equals(targetPosition)) {
            mob.getNavigator().stepRandom(false);
            return Direction.NONE;
        }

        Direction nextDirection = Direction.between(position, targetPosition);

        // When diagonally adjacent, attempt to step into one of the cardinal components.
        if (policy.getDistance() == 1 && position.isWithinDistance(targetPosition, 1) && nextDirection.isDiagonal()) {
            for (Direction dir : Direction.diagonalComponents(nextDirection)) {
                if (collision.traversable(position, EntityType.NPC, dir)) {
                    return dir;
                }
            }
            return Direction.NONE;
        }

        // No movement is needed if the target is already reachable.
        if (collision.reached(position, target, policy)) {
            return Direction.NONE;
        }

        int dx = nextDirection.getTranslateX();
        int dy = nextDirection.getTranslateY();
        Direction directionX = dx > 0 ? Direction.EAST : dx < 0 ? Direction.WEST : Direction.NONE;
        Direction directionY = dy > 0 ? Direction.NORTH : dy < 0 ? Direction.SOUTH : Direction.NONE;

        // Try the direct diagonal or straight-line direction first.
        if (collision.traversable(position, EntityType.NPC, nextDirection)) {
            return nextDirection;
        }

        // Fall back to the x-axis component.
        if (directionX != Direction.NONE && collision.traversable(position, EntityType.NPC, directionX)) {
            return directionX;
        }

        // Fall back to the y-axis component.
        if (directionY != Direction.NONE && collision.traversable(position, EntityType.NPC, directionY)) {
            return directionY;
        }

        // No valid movement direction was found.
        return Direction.NONE;
    }
}