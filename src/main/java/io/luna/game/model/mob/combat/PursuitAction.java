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

import java.util.function.Predicate;

/**
 * An {@link Action} that moves a {@link Npc} toward its current combat target.
 * <p>
 * This action is used while combat is active but the attacker has not yet reached a valid interaction position. It
 * resolves pursuit manually one step at a time using collision checks.
 * <p>
 * Because this is a weak action, it may be replaced by stronger actions whenever necessary.
 *
 * @author lare96
 */
public final class PursuitAction extends Action<Npc> {

    /**
     * The combat context for the pursuing NPC.
     */
    private final CombatContext<?> combat;

    /**
     * The current attack's interaction policy, refreshed by the combat loop before pursuit executes.
     */
    private InteractionPolicy policy;

    /**
     * Creates a new {@link PursuitAction}.
     *
     * @param npc The NPC performing pursuit.
     * @param policy The interaction policy used to test valid reachability.
     */
    public PursuitAction(Npc npc, InteractionPolicy policy) {
        super(npc, ActionType.WEAK);
        this.policy = policy;
        combat = npc.getCombat();
    }

    /**
     * Updates pursuit to use the attack already selected by the combat loop.
     * This preserves queued movement and avoids invoking NPC attack-selection hooks a second time.
     *
     * @param policy The selected attack's current reach rules.
     */
    public void setPolicy(InteractionPolicy policy) {
        this.policy = policy;
    }

    /**
     * Attempts to queue the next pursuit step for this NPC.
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
        if (!canPursue(combat.getTarget())) {
            complete();
            return;
        }
        // Cancel any in-progress navigation while pursuing.
        mob.getNavigator().cancel();

        if (tryStep()) {
            complete();
        }
    }

    @Override
    public boolean run() {
        Mob target = combat.getTarget();
        if (!canPursue(target)) {
            return true;
        }
        // Only a valid chase may take control of navigation.
        mob.getNavigator().cancel();
        mob.interact(target);
        return tryStep();
    }

    /**
     * Checks pursuit eligibility before cancelling navigation or queuing movement.
     * An obsolete chase must leave replacement navigation alone. Dead or disabled attackers and missing,
     * dead, or out-of-view targets cannot authorize another pursuit step.
     *
     * @param target The current combat target, or {@code null} if combat has ended.
     * @return Whether this NPC may continue pursuing the target.
     */
    private boolean canPursue(Mob target) {
        return mob.isAlive() && !combat.isDisabled() && target != null && target.isAlive() &&
                mob.isViewableFrom(target) && mob.getCombat().isWithinLeash(target) &&
                !mob.getCombat().isRetreating();
    }

    /**
     * Computes the next single-step movement direction needed to pursue the current combat target.
     * <p>
     * This method performs simple collision-aware chase logic:
     * <ul>
     *     <li>Rejects missing or off-plane targets</li>
     *     <li>Tries to separate overlapping entities</li>
     *     <li>Stops if the target is already reachable under the current interaction policy</li>
     *     <li>Handles unreachable close diagonal adjacency by attempting a component step</li>
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

        return nextStep(position, targetPosition, policy.getDistance(), collision.reached(mob, target, policy),
                direction -> collision.traversable(position, EntityType.NPC, direction, mob.size(),
                        mob.getRouteStrategy()));
    }

    /**
     * Selects a chase step without changing game state. Map planning uses the same directional choices as live pursuit.
     * Overlapping tiles have no step here; live pursuit handles their random separation before calling this method.
     *
     * @param position The pursuer's current south west tile.
     * @param targetPosition The target's tile.
     * @param distance The selected attack's reach distance.
     * @param reached Whether the selected attack can already reach the target.
     * @param traversable Tests a single step under the caller's collision rules.
     * @return The next chase direction, or {@link Direction#NONE} when no step is possible or needed.
     */
    public static Direction nextStep(Position position, Position targetPosition, int distance, boolean reached,
                                     Predicate<Direction> traversable) {
        if (position.getZ() != targetPosition.getZ() || position.equals(targetPosition)) {
            return Direction.NONE;
        }
        // Footprint contact may already be valid even when the southwest anchors are diagonally adjacent.
        if (reached) {
            return Direction.NONE;
        }
        Direction nextDirection = Direction.between(position, targetPosition);

        // When diagonally adjacent, attempt to step into one of the cardinal components.
        if (distance == 1 && position.isWithinDistance(targetPosition, 1) && nextDirection.isDiagonal()) {
            for (Direction dir : Direction.diagonalComponents(nextDirection)) {
                if (traversable.test(dir)) {
                    return dir;
                }
            }
            return Direction.NONE;
        }

        int dx = nextDirection.getTranslateX();
        int dy = nextDirection.getTranslateY();
        Direction directionX = dx > 0 ? Direction.EAST : dx < 0 ? Direction.WEST : Direction.NONE;
        Direction directionY = dy > 0 ? Direction.NORTH : dy < 0 ? Direction.SOUTH : Direction.NONE;

        // Try the direct diagonal or straight-line direction first.
        if (traversable.test(nextDirection)) {
            return nextDirection;
        }

        // Fall back to the x-axis component.
        if (directionX != Direction.NONE && traversable.test(directionX)) {
            return directionX;
        }

        // Fall back to the y-axis component.
        if (directionY != Direction.NONE && traversable.test(directionY)) {
            return directionY;
        }

        // No valid movement direction was found.
        return Direction.NONE;
    }
}
