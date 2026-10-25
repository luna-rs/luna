package io.luna.game.model.mob.combat;

import api.combat.player.PlayerCombatHandler;
import io.luna.game.action.Action;
import io.luna.game.action.ActionType;
import io.luna.game.model.Position;
import io.luna.game.model.mob.Mob;
import io.luna.game.model.mob.Npc;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.bot.Bot;
import io.luna.game.model.mob.combat.attack.CombatAttack;
import io.luna.game.model.mob.combat.state.CombatContext;
import io.luna.game.model.mob.interact.InteractionPolicy;
import io.luna.game.model.mob.movement.NavigationRequest;

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

    /** Give a pending engagement 30 ticks without movement or an attack before abandoning it. */
    private static final int MAX_STALLED_TICKS = 30;
    private Position lastProgressPosition;
    private Mob pendingTarget;
    private int stalledTicks;

    /**
     * Creates a new {@link CombatAction} for the specified mob.
     *
     * @param mob The mob executing combat.
     */
    public CombatAction(Mob mob) {
        // TODO@0.5.0 Combat should be taken outside of the action system. It needs to run BEFORE all actions.
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
        if (combat.getAutoRetaliateTarget() != null && (combat.isAutoRetaliate() || mob instanceof Bot)) {
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

        if (mob instanceof Player) {
            updatePlayerPursuit(target, policy, reached);
        } else if (mob instanceof Npc) {
            updateNpcPursuit(policy, reached);
        }

        // Continue pursuing until the target is inside interaction range.
        if (!reached) {
            if (mob.getWalking().isEmpty() && mob instanceof Npc) {
                // We are stationary and still not in range, so re-initiate pursuit.
                mob.interact(target);
                mob.getActions().submitIfAbsent(new PursuitAction(mob.asNpc(), policy));
            }
            // Stay active while combat should continue.
            return pendingAttackExpired(target);
        }

        // Run final close-range combat checks before attacking.
        if (!check()) {
            return clearTarget();
        }

        // Check if any listeners preemptively stop the attack.
        if (mob instanceof Player && PlayerCombatHandler.INSTANCE.testStopAttack((Player) mob, attack)) {
            return clearTarget();
        }

        // Attack as soon as the combat delay permits it.
        if (combat.isAttackReady() || attack.isIgnoreAttackDelay()) {
            mob.interact(target);
            attack.apply();
            stalledTicks = 0;
            return false;
        }
        return pendingAttackExpired(target);
    }

    /**
     * Starts pursuit when out of range or refreshes an existing chase after attack reach changes.
     * Queued movement is preserved while the selected attack remains out of range. Once it can reach,
     * the active chase and its queued steps are cancelled, including pending path results. Pursuit resumes
     * if the target later leaves range. Matching out-of-range policies retain the existing navigation request.
     *
     * @param target The current combat target.
     * @param policy The selected attack's reach rules.
     * @param reached Whether the selected attack can already reach the target.
     */
    private void updatePlayerPursuit(Mob target, InteractionPolicy policy, boolean reached) {
        var navigator = mob.getNavigator();
        boolean chasingTarget = target.equals(navigator.getCurrentTarget());
        if (reached) {
            if (chasingTarget) {
                navigator.cancel();
                mob.getWalking().clear();
            }
            return;
        }
        if (!navigator.isFollowing(target, policy)) {
            var request = NavigationRequest.builder(mob).policy(policy).continuous(true).target(target).build();
            navigator.submit(request);
        }
    }

    /**
     * Refreshes an existing NPC chase with the selected attack's reach rules.
     * Movement is retained while out of range. Once in range, finishing pursuit prevents another queued
     * execution from adding a step, and clearing its walking steps stops the old chase.
     *
     * @param policy The selected attack's reach rules.
     * @param reached Whether the selected attack can already reach the target.
     */
    private void updateNpcPursuit(InteractionPolicy policy, boolean reached) {
        PursuitAction pursuit = mob.getActions().first(PursuitAction.class);
        if (pursuit != null) {
            if (reached) {
                pursuit.complete();
                mob.getWalking().clear();
            } else {
                pursuit.setPolicy(policy);
            }
        }
    }

    /** The combat timer starts after an attack; it cannot decide whether a first attack is still pending. */
    private boolean pendingAttackExpired(Mob target) {
        Position position = mob.getPosition();
        if (pendingTarget != target || !position.equals(lastProgressPosition)) {
            pendingTarget = target;
            lastProgressPosition = position;
            stalledTicks = 0;
        } else {
            stalledTicks++;
        }
        if (stalledTicks >= MAX_STALLED_TICKS && !combat.inCombat()) {
            if (target.equals(mob.getNavigator().getCurrentTarget()) && mob.getNavigator().isCurrentContinuous()) {
                mob.getNavigator().cancel();
            }
            return clearTarget();
        }
        return false;
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
            if (mob.getStatus().isImmobilized()) {
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
        if (mob instanceof Npc) {
            PursuitAction pursuit = mob.getActions().first(PursuitAction.class);
            if (pursuit != null && !pursuit.isFinished()) {
                pursuit.complete();
                mob.getWalking().clear();
            }
        }
        combat.setTarget(null);
        return !combat.inCombat();
    }
}
