package io.luna.game.model.mob.combat;

import io.luna.game.action.Action;
import io.luna.game.action.ActionType;
import io.luna.game.model.Direction;
import io.luna.game.model.EntityState;
import io.luna.game.model.Position;
import io.luna.game.model.collision.CollisionManager;
import io.luna.game.model.mob.Mob;
import io.luna.game.model.mob.Npc;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.interact.InteractionPolicy;

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
     * An {@link Action} that applies {@link CombatDamage} on the first execution, and attempts to auto-retaliate on
     * the second execution.
     * <p>
     * The {@code attacker} is the mob that initiated the hit, and the {@code victim} is the mob that may retaliate
     * against that attacker.
     */
    private static final class CombatDamageAction extends Action<Mob> {

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
         * The combat damage to apply.
         */
        private final CombatDamage damage;

        /**
         * Creates a new {@link CombatDamageAction}.
         *
         * @param attacker The mob that initiated the attack.
         * @param victim The mob that will retaliate.
         * @param damage The combat damage to apply.
         */
        public CombatDamageAction(Mob attacker, Mob victim, CombatDamage damage) {
            super(victim, ActionType.SOFT, true, 1);
            this.attacker = attacker;
            this.victim = victim;
            this.damage = damage;
        }

        @Override
        public boolean run() {
            if (getExecutions() == 0) {
                // First execution: apply damage.
                damage.apply();
            } else if (getExecutions() == 1 && victim.getCombat().isAutoRetaliate() &&
                    (victim.getWalking().isEmpty() || victim instanceof Npc)) {
                // TODO Maybe a "shouldAutoRetaliate" is needed? A retreating NPC should no longer retaliate.
                //  Retreating should be done in a STRONG action.
                // TODO Can then do cool things like loop the world when NPC spawn dumps are added, and make all
                //  spawned guards retreat when leashed too far (along with all other relevant world NPCs).

                // Second execution: if the victim retaliates, and is an NPC or stationary, attack back.
                victim.getCombat().attack(attacker);
            }
            return getExecutions() == 1;
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
        Position position = mob.getPosition();
        CollisionManager collisionManager = mob.getWorld().getCollisionManager();
        InteractionPolicy interactionPolicy = combat.computeInteractionPolicy();

        // Ensure attacker and the target are both valid and active.
        if (target == null ||
                mob.getState() == EntityState.INACTIVE ||
                target.getState() == EntityState.INACTIVE ||
                mob.getHealth() == 0 ||
                target.getHealth() == 0) {
            return true;
        }
        // todo npc retreating

        // Check if we've reached the target before proceeding. If we have, stop moving.
        if (!collisionManager.reached(position, target, interactionPolicy)) {
            if (mob.getWalking().isEmpty()) {
                // We haven't reached the target, and we're stationary. Move towards interaction range.
                mob.getNavigator().walkTo(target, Optional.empty(), false);
            }
            return false;
        }
        mob.getWalking().clear();

        // Ensure proper semantics when we're on the same tile as our target.
        if (target.getPosition().equals(position)) {
            if (mob instanceof Player) {
                // TODO Is this just for melee? Or can you attack with magic/ranged?
                // Players cannot attack when on the same position.
                return false;
            } else if (mob.getWalking().isEmpty()) {
                Direction dir = target.getLastDirection().opposite();
                if (!mob.getNavigator().step(dir)) {
                    mob.getNavigator().stepRandom(false);
                }
            }
        }

        if (combat.isAttackReady() && Objects.equals(mob.getInteractingWith(), combat.getTarget())) {
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
        // TODO Maybe we need a getRetaliationTarget() or something? https://i.imgur.com/Sv21DM3.png

        mob.animation(combat.getAttackAnimation());
        victim.animation(combat.getDefenceAnimation());
        victim.getCombat().resetCombatTimer();
        victim.submitAction(new CombatDamageAction(mob, victim, damage));
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
}
