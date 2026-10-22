package io.luna.game.model.mob.combat;

import api.combat.player.PlayerCombatHandler;
import io.luna.game.action.Action;
import io.luna.game.action.ActionPhase;
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
import io.luna.game.model.mob.movement.NavigationResult;

import java.util.concurrent.CompletableFuture;

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
 * <p>
 * This weak action executes in {@link ActionPhase#COMBAT}, after incoming effects and ordinary actions. If its
 * selected attack is out of range, {@link #runAfterMovement()} may retry that attack after walking without
 * repeating attack selection or per-tick processing hooks. Attack readiness belongs to {@link CombatContext}
 * and continues advancing independently of this action's interruption lifecycle.
 * <p>
 * Clearing a target stops pursuit immediately. The action may remain registered until recent-combat status
 * expires, after which normal completion performs the remaining combat cleanup.
 *
 * @author lare96
 */
public final class CombatAction extends Action<Mob> {

    /**
     * The cached combat state for {@link #mob}.
     */
    private final CombatContext<?> combat;

    /**
     * The number of stalled ticks allowed before an engagement outside recent combat is abandoned.
     */
    private static final int MAX_STALLED_TICKS = 30;

    /**
     * The last position observed while tracking progress toward a pending attack.
     */
    private Position lastProgressPosition;

    /**
     * The target whose pending attack is being checked for stalled progress.
     */
    private Mob pendingTarget;

    /**
     * The consecutive processing cycles without target or position progress.
     */
    private int stalledTicks;

    /**
     * The out-of-range attack selected before walking, or {@code null} when no reach retry is pending.
     */
    private CombatAttack<?> pendingAttack;

    /**
     * The target captured with {@link #pendingAttack}, used to reject a retry after a target change.
     */
    private Mob pendingAttackTarget;

    /**
     * Identifies the player navigation request used by this combat action. Cleanup compares the pending result by
     * identity so that a newer movement request is not cancelled when this combat action ends.
     */
    private CompletableFuture<NavigationResult> playerPursuit;

    /**
     * Creates a new {@link CombatAction} for the specified mob.
     *
     * @param mob The mob executing combat.
     */
    public CombatAction(Mob mob) {
        super(mob, ActionType.WEAK);
        combat = mob.getCombat();
    }

    /**
     * @return {@link ActionPhase#COMBAT}, after effects and reach-based interaction listeners.
     */
    @Override
    public ActionPhase getPhase() {
        return ActionPhase.COMBAT;
    }

    /**
     * Rechecks the selected attack's reach and legality after walking.
     * <p>
     * Only an attack that was out of range before walking is eligible. The pending reference is consumed before
     * retrying, preventing a second swing from another retry call. A changed target discards the retry.
     *
     * @return {@code true} if combat should finish, otherwise {@code false}.
     */
    @Override
    public boolean runAfterMovement() {
        CombatAttack<?> attack = pendingAttack;
        pendingAttack = null;
        Mob target = combat.getTarget();
        if (attack == null || target != pendingAttackTarget) {
            return false;
        }
        if (!mob.isAlive() || !target.isAlive() || combat.isDisabled() ||
                !mob.isWithinDistance(target, Position.VIEWING_DISTANCE + 5) ||
                (mob instanceof Player player && !player.getCombat().canAttackCurrentTarget()) ||
                (mob instanceof Npc npc && !npc.getCombat().isWithinLeash(target))) {
            return clearTarget();
        }
        if (!mob.getWorld().getCollisionManager().reached(mob.getPosition(), target,
                attack.getInteractionPolicy())) {
            return false;
        }
        if (mob instanceof Player) {
            updatePlayerPursuit(target, attack.getInteractionPolicy(), true);
        } else if (mob instanceof Npc) {
            updateNpcPursuit(attack.getInteractionPolicy(), true);
        }
        return tryAttack(attack);
    }

    @Override
    public boolean run() {
        pendingAttack = null;

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
        if (target == null || !target.isAlive() || combat.isDisabled() || !CombatRestriction.permits(mob, target)) {
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
            pendingAttack = attack;
            pendingAttackTarget = target;
            if (mob.getWalking().isEmpty() && mob instanceof Npc) {
                // We are stationary and still not in range, so re-initiate pursuit.
                mob.interact(target);
                mob.getActions().submitIfAbsent(new PursuitAction(mob.asNpc(), policy));
            }
            // Stay active while combat should continue.
            return pendingAttackExpired(target);
        }

        return tryAttack(attack);
    }

    /**
     * Performs final combat checks and applies an already-selected attack when its cooldown permits it.
     * <p>
     * Single-combat ownership, same-tile movement, and player stop-attack listeners are checked here for both
     * the initial attempt and the post-movement retry.
     *
     * @param attack The attack selected for the current target.
     * @return {@code true} if combat should finish, otherwise {@code false}.
     */
    private boolean tryAttack(CombatAttack<?> attack) {
        Mob target = combat.getTarget();
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
        if (((Player) mob).getCombat().isPursuitHeld(target)) {
            cancelPlayerPursuit();
            return;
        }
        boolean chasingTarget = target.equals(navigator.getCurrentTarget());
        if (reached) {
            if (chasingTarget) {
                navigator.cancel();
                mob.getWalking().clear();
            }
            playerPursuit = null;
            return;
        }
        if (!navigator.isFollowing(target, policy)) {
            var request = NavigationRequest.builder(mob).policy(policy).continuous(true).target(target).build();
            navigator.submit(request);
        }
        playerPursuit = navigator.getCurrentPending();
    }

    /**
     * Cancels this combat action's active player chase and queued steps, including paths still being computed.
     * A replacement request belongs to a newer movement command and is left untouched.
     */
    private void cancelPlayerPursuit() {
        if (playerPursuit != null) {
            var navigator = mob.getNavigator();
            if (navigator.getCurrentPending() == playerPursuit) {
                navigator.cancel();
                mob.getWalking().clear();
            }
            playerPursuit = null;
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

    /**
     * Tracks progress while waiting to reach a target or finish an attack cooldown.
     * <p>
     * Target changes, position changes, and successful attacks reset the stalled count. A pending engagement is
     * abandoned only after {@link #MAX_STALLED_TICKS} stalled cycles and when recent-combat status has expired.
     *
     * @param target The target of the pending attack.
     * @return {@code true} if the action should finish, otherwise {@code false}.
     */
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
            return clearTarget();
        }
        return false;
    }

    @Override
    public void onFinished() {
        pendingAttack = null;
        cancelPlayerPursuit();
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
     * Cancels pursuit immediately even if the combat timer keeps this action alive for subsequent ticks.
     * <p>
     * The remaining cleanup is expected to occur when the action finishes.
     *
     * @return {@code true} if the action should finish now, otherwise {@code false}.
     */
    private boolean clearTarget() {
        cancelPlayerPursuit();
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
