package io.luna.game.model.mob.combat;

import io.luna.game.action.Action;
import io.luna.game.action.ActionType;
import io.luna.game.model.Direction;
import io.luna.game.model.EntityState;
import io.luna.game.model.Position;
import io.luna.game.model.collision.CollisionManager;
import io.luna.game.model.mob.Mob;
import io.luna.game.model.mob.Npc;
import io.luna.game.model.mob.interact.InteractionPolicy;
import io.luna.game.task.Task;

import java.util.Objects;
import java.util.Optional;

/**
 * An {@link Action} that executes the active combat loop for a mob against its current
 * {@link CombatContext#getTarget() target}.
 * <p>
 * This action is responsible for maintaining combat state while an engagement is active. Each cycle it validates
 * both participants, moves the attacker into interaction range when necessary, resolves NPC overlap behavior, checks
 * attack timing, and dispatches the appropriate combat strike when ready.
 *
 * @author lare96
 */
public final class CombatAction extends Action<Mob> {

    /**
     * A one-tick action that triggers auto-retaliation for a victim after being attacked.
     * <p>
     * This is queued onto the victim rather than executed immediately so retaliation happens through the normal
     * action system.
     * <p>
     * The {@code attacker} is the mob that initiated the hit, and the {@code victim} is the mob that may
     * retaliate against that attacker.
     */
    private static final class CombatRetaliateAction extends Action<Mob> {

        /**
         * The mob that originally attacked the victim and will become the retaliation target.
         */
        private final Mob attacker;

        /**
         * The mob that may auto-retaliate against the attacker.
         * <p>
         * This is also the owning mob for this action.
         */
        private final Mob victim;

        /**
         * Creates a new retaliation action.
         *
         * @param attacker The mob that initiated the attack.
         * @param victim The mob that will retaliate.
         */
        public CombatRetaliateAction(Mob attacker, Mob victim) {
            super(victim, ActionType.WEAK, false, 1);
            this.attacker = attacker;
            this.victim = victim;
        }

        @Override
        public boolean run() {
            victim.getCombat().attack(attacker);
            return true;
        }
    }

    /**
     * Cached combat context for the owning mob.
     */
    private final CombatContext combat;

    /**
     * Creates a new {@link CombatAction} for {@code mob}.
     *
     * @param mob The mob executing combat.
     */
    public CombatAction(Mob mob) {
        super(mob, ActionType.WEAK, true, 1);
        combat = mob.getCombat();
    }

    @Override
    public boolean run() {
        Mob target = combat.getTarget();
        if (target == null ||
                mob.getState() == EntityState.INACTIVE ||
                target.getState() == EntityState.INACTIVE ||
                mob.getHealth() == 0 ||
                target.getHealth() == 0) {
            return true;
        }
        // todo npc retreating
        // todo npc dancing around player when within 1 square? maybe clear walking queue when within reach?

        Position position = mob.getPosition();
        CollisionManager collisionManager = mob.getWorld().getCollisionManager();
        InteractionPolicy interactionPolicy = combat.computeInteractionPolicy();
        if (!collisionManager.reached(position, target, interactionPolicy)) {
            if (mob.getWalking().isEmpty()) {
                mob.getNavigator().walkTo(target, Optional.empty(), false);
            }
            return false;
        }

        // TODO Players shouldn't be allowed to attack at all if on the same position? Unless it's magic/ranged?
        if (position.equals(target.getPosition()) && mob instanceof Npc) {
            if (mob.getWalking().isEmpty()) {
                Direction dir = target.getLastDirection().opposite();
                if (!mob.getNavigator().step(dir)) {
                    mob.getNavigator().stepRandom(false);
                }
            }
        }
        if (combat.isAttackReady() && Objects.equals(mob.getInteractingWith(), combat.getTarget())) {
            mob.getWalking().clear();
            combat.resetAttackDelay();
            combat.resetCombatTimer();
            launchMeleeAttack();
            return false;
        }
        return !combat.inCombat();
    }

    @Override
    public void onFinished() {
        // todo npc retreat back to home if needed
        combat.setTarget(null);
    }

    /**
     * Resolves a melee attack against the current target.
     */
    private void launchMeleeAttack() {
        Mob victim = combat.getTarget();
        CombatDamage damage = CombatDamage.computed(mob, victim, CombatDamageType.MELEE);

        mob.animation(combat.getAttackAnimation());

        world.schedule(new Task(false, 1) {
            @Override
            protected void execute() {
                damage.apply();
                cancel();
            }
        });
        victim.animation(combat.getDefenceAnimation());
        victim.getCombat().resetCombatTimer();
        retaliate();
    }

    /**
     * Resolves a ranged attack against the current target.
     */
    private void launchRangedAttack() {
    }

    /**
     * Resolves a magic attack against the current target.
     */
    private void launchMagicAttack() {
    }

    /**
     * Queues auto-retaliation for the current target when enabled.
     * <p>
     * Retaliation is deferred through a one-tick weak action rather than being executed inline with the current
     * attack cycle.
     */
    private void retaliate() {
        Mob victim = combat.getTarget();
        // TODO How does auto-retaliate work? Does it change focus for you if you're already fighting and someone else
        //  attacks you? Or does it stay with the person you were initially fighting?
        //  For now, we just make it change who we're fighting everytime.

        // TODO For some reason, swapping weapons will make it so the player stops auto retaliating? Or ending the
        //  action at all?
        if (victim.getCombat().isAutoRetaliate()) {
            victim.getActions().submitIfAbsent(new CombatRetaliateAction(mob, victim));
        }
    }
}
