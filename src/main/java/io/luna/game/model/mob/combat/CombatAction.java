package io.luna.game.model.mob.combat;

import io.luna.game.action.Action;
import io.luna.game.action.ActionType;
import io.luna.game.model.EntityState;
import io.luna.game.model.Position;
import io.luna.game.model.collision.CollisionManager;
import io.luna.game.model.mob.Mob;
import io.luna.game.model.mob.Npc;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.interact.InteractionPolicy;
import io.luna.game.model.mob.interact.InteractionType;

import java.util.Objects;

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
            // todo does the actual swing need to be moved here too?? sometimes i notice NPCs attack a little too early
            if (getExecutions() == 0) {
                // First execution: apply damage.
                damage.apply();
            } else if (getExecutions() == 1 && victim.getCombat().isAutoRetaliate() &&
                    (victim.getWalking().isEmpty() || victim instanceof Npc)) {
                // TODO getRetaliationTarget() function might be needed? https://i.imgur.com/Sv21DM3.png
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

        mob.interact(target);

        // Apply hook from context and short-circuit if necessary.
        boolean reached = collisionManager.reached(position, target, interactionPolicy);
        if (!combat.onCombatHook(reached)) {
            return false;
        }
        if (mob instanceof Player)
            logger.trace("Combat hook cleared.");

        // Check if we've reached the target before proceeding. If we have, stop moving.
        if (!reached) {
            if (mob instanceof Player)
                logger.trace("We have not reached.");
            if (mob.getWalking().isEmpty()) {
                if (mob instanceof Player)
                    logger.trace("Walking is empty, starting chase.");
                // We haven't reached the target, and we're stationary. Move towards interaction range.
                mob.getActions().submitIfAbsent(new PursuitAction(mob, interactionPolicy));
            }
            return false;
        }

        // Reached the entity, check for adjacent obstacle in the way if the policy is close-range melee combat.
        if (interactionPolicy.getType() == InteractionType.SIZE &&
                interactionPolicy.getDistance() == 1 && !checkMeleeCollision(target)) {
            return false;
        }
        if (mob instanceof Player)
            logger.trace("Ready to launch attack.");

        // All checks passed, launch attack if ready.
        if (combat.isAttackReady() && Objects.equals(mob.getInteractingWith(), combat.getTarget())) {
            if (mob instanceof Player)
                logger.trace("Launching attack.");
            combat.resetAttackDelay();
            combat.resetCombatTimer();
            launchMeleeAttack();
            return false;
        }
        return !combat.inCombat();
    }

    @Override
    public void onFinished() {
        // todo NPC that has drifted too far, start wandering back to home? Specialized wandering action for this?
        // todo maybe a less aggressive version that does it more periodically?
        combat.setTarget(null);
        mob.interact(null);
    }

    /**
     * Resolves a melee attack against the current target.
     */
    private void launchMeleeAttack() {
        Mob victim = combat.getTarget();
        CombatDamage damage = CombatDamage.computed(mob, victim, CombatDamageType.MELEE);

        mob.animation(combat.getAttackAnimation());
        victim.animation(victim.getCombat().getDefenceAnimation());
        victim.getCombat().resetCombatTimer();
        victim.submitAction(new CombatDamageAction(mob, victim, damage));
    }

    /**
     * Resolves a ranged attack against the current target.
     */
    private void launchRangedAttack() {
        // todo ammo defs (projectiles, etc)
    }

    /**
     * Resolves a magic attack against the current target.
     */
    private void launchMagicAttack() {
        // todo radius spells won't target if player occupies same pos as caster
        // todo autocasting interfaces, use <x> spell on <x> setup, current spell vs weapon?
        // todo apply spells effects from CombatSpellHandler#effect
    }

    public boolean checkMeleeCollision(Mob target) {
      /*  Position position = mob.getPosition();
        Position targetPosition = target.getPosition();
        Direction dir = Direction.between(position, targetPosition);

        if (dir == Direction.NONE || dir.isDiagonal()) {
            return false;
        }
        for(GameObject object : world.findOnTile(targetPosition, GameObject.class)) {
            if (CollisionUpdate.unwalkable(object.getDefinition(), object.getObjectType().getId())) {
                return false;
            }
        }*/
        // TODO Need a way to check collision map for objects only..
        return true;
    }
}
