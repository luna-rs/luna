package io.luna.game.model.mob.combat;

import io.luna.game.action.Action;
import io.luna.game.action.ActionType;
import io.luna.game.model.Position;
import io.luna.game.model.mob.Mob;
import io.luna.game.model.mob.combat.attack.CombatAttack;
import io.luna.game.model.mob.combat.state.CombatContext;
import io.luna.game.model.mob.interact.InteractionPolicy;

/**
 * An {@link Action} that drives a mob's active combat loop against its current
 * {@link CombatContext#getTarget() target}.
 * <p>
 * Each cycle performs the full combat maintenance flow:
 * <ul>
 *     <li>ends immediately if the attacker dies,</li>
 *     <li>cleans up stale combat references,</li>
 *     <li>handles auto-retaliate target acquisition,</li>
 *     <li>validates the current target,</li>
 *     <li>selects the next attack style,</li>
 *     <li>checks interaction range and submits pursuit if needed,</li>
 *     <li>runs per-tick combat processing hooks, and</li>
 *     <li>fires the attack once the combat delay is ready.</li>
 * </ul>
 * The action remains active while combat should continue and finishes once the
 * target is cleared or combat otherwise ends.
 *
 * @author lare96
 */
public final class CombatAction extends Action<Mob> {

    /**
     * The cached combat state for {@link #mob}.
     */
    private final CombatContext<?> combat;

    /**
     * Creates a new {@link CombatAction} for the specified mob.
     *
     * @param mob The mob executing combat.
     */
    public CombatAction(Mob mob) {
        super(mob, ActionType.WEAK);
        combat = mob.getCombat();
    }

    @Override
    public boolean run() {

        // End the action immediately if the attacker is dead.
        if (!mob.isAlive()) {
            return true;
        }

        // Clear stale single-combat memory when the last opponent has died.
        // This allows the mob to attack something else immediately.
        if (combat.getLastCombatWith() != null && !combat.getLastCombatWith().isAlive()) {
            combat.setLastCombatWith(null);
        }

        // Apply auto-retaliate if a valid aggressor was remembered.
        if (combat.getAutoRetaliateTarget() != null && combat.isAutoRetaliate()) {
            if (combat.getAutoRetaliateTarget().isAlive()) {
                combat.setTarget(combat.getAutoRetaliateTarget());
            }
            combat.setAutoRetaliateTarget(null);
        }

        // Stop combat if the current target is no longer valid.
        Mob target = combat.getTarget();
        if (target == null || !target.isAlive() || combat.isDisabled()) {
            return clearTarget();
        }

        // Stop combat if the target has moved too far outside local combat/view range.
        if (!mob.isWithinDistance(target, Position.VIEWING_DISTANCE + 5)) {
            return clearTarget();
        }

        // Resolve the next usable combat attack against this target.
        CombatAttack<?> attack = mob.getCombat().getNextAttack(target);
        if (attack == null) {
            return clearTarget();
        }

        // Run ongoing combat processing using the current interaction reach result.
        InteractionPolicy policy = attack.getInteractionPolicy();
        boolean reached = mob.getWorld().getCollisionManager().reached(mob.getPosition(), target, policy);
        if (!combat.onCombatProcess(reached)) {
            return clearTarget();
        }

        // Continue pursuing until the target is inside interaction range.
        if (!reached) {
            if (mob.getWalking().isEmpty()) {
                // We are stationary and still not in range, so re-initiate pursuit.
                mob.interact(target);
                mob.getActions().submitIfAbsent(new PursuitAction(mob, policy));
            }
            // Stay active while combat should continue.
            return !combat.inCombat();
        }

        // Run final close-range combat checks before attacking.
        if (!check()) {
            return clearTarget();
        }

        // Attack as soon as the combat delay permits it.
        if (combat.isAttackReady() || attack.isIgnoreAttackDelay()) {
            mob.interact(target);
            attack.apply();
            return false;
        }
        return !combat.inCombat();
    }

    @Override
    public void onFinished() {
        combat.onCombatFinished();
        combat.setTarget(null);
        combat.setAutoRetaliateTarget(null);
        mob.interact(null);
    }

    /**
     * Performs final validation once the target is in range.
     *
     * @return {@code true} if combat may proceed, otherwise {@code false}.
     */
    private boolean check() {
        Mob target = combat.getTarget();
        if (!combat.checkMultiCombat(target)) {
            return false;
        } else if (mob.getPosition().equals(target.getPosition())) {
            if (mob.getCombat().isImmobilized()) {
                // If we're frozen and target is occupying our tile, we cannot attack.
                return false;
            }
            // Otherwise, we can attack if we're able to move.
            return !mob.getWalking().isEmpty() || mob.getNavigator().step(target.getLastDirection().opposite()) ||
                    mob.getNavigator().stepRandom(false);
        }
        return true;
    }

    /**
     * Clears the current combat target without performing full combat teardown.
     * <p>
     * The remaining cleanup is expected to occur when the action finishes.
     *
     * @return {@code true} if the action should finish now, otherwise {@code false}.
     */
    private boolean clearTarget() {
        combat.setTarget(null);
        return !combat.inCombat();
    }
}