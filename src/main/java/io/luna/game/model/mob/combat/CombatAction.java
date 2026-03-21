package io.luna.game.model.mob.combat;

import io.luna.game.action.Action;
import io.luna.game.action.ActionType;
import io.luna.game.model.Direction;
import io.luna.game.model.EntityState;
import io.luna.game.model.EntityType;
import io.luna.game.model.Position;
import io.luna.game.model.collision.CollisionManager;
import io.luna.game.model.mob.Mob;
import io.luna.game.model.mob.Npc;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.interact.InteractionPolicy;
import io.luna.game.model.mob.interact.InteractionType;

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
     * An {@link Action} that performs a single movement request toward the mob's current combat target.
     * <p>
     * This action is intended as a lightweight movement trigger for combat pursuit. When processed, it asks the mob's
     * navigator to path toward the active {@link CombatContext#getTarget() combat target}, then immediately completes.
     */
    private static final class PursuitAction extends Action<Npc> {
        private final InteractionPolicy policy;

        /**
         * Creates a new {@link PursuitAction}.
         *
         * @param mob The mob that should chase its current combat target.
         */
        public PursuitAction(Npc mob, InteractionPolicy policy) {
            super(mob, ActionType.WEAK, true, 1);
            this.policy = policy;
        }

        private boolean tryStep() {
            Direction nextDirection = computeNextStepDirection(mob, mob.getCombat().getTarget(), policy);
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
            if (tryStep()) {
                interrupt();
            }
        }

        @Override
        public boolean run() {
            return tryStep();
        }

        public Direction computeNextStepDirection(Npc self, Mob target, InteractionPolicy policy) {
            CollisionManager collision = self.getWorld().getCollisionManager();

            // Target is not on same plane, or non-existent.
            if (target == null || self.getZ() != target.getZ()) {
                return Direction.NONE;
            }

            // We're already occupying the same tile, combat hook will move NPC.
            Position position = self.getPosition();
            if (position.equals(target.getPosition())) {
                return Direction.NONE;
            }

            // We're already in perfect interaction distance and position.
            Direction nextDirection = Direction.between(self.getPosition(), target.getPosition());
            if (!nextDirection.isDiagonal() && collision.reached(self.getPosition(), target, policy)) {
                return Direction.NONE; // todo might need a size check for the "isdiagonal" the diagonal policy
                // only exists for a range of 1 i think
            }

            // Special OSRS-like melee rule when diagonal to target:
            // don't step diagonally into contact; prefer x only.
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

            // Normal dumb travelling pattern
            if (collision.traversable(position, EntityType.NPC, nextDirection)) {
                return nextDirection;
            }
            if (directionX != Direction.NONE && collision.traversable(position, EntityType.NPC, directionX)) {
                return directionX;
            }

            if (directionY != Direction.NONE && position.computeLongestDistance(target.getPosition()) > 1 &&
                    collision.traversable(position, EntityType.NPC, directionY)) {
                return directionY;
            }

            return Direction.NONE;
        }
    }

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

        boolean reached = collisionManager.reached(position, target, interactionPolicy);
        if (reached && interactionPolicy.getType() == InteractionType.LINE_OF_SIGHT) {
            // Clear walking queue as soon as we've reached the target for LOS.
            mob.getWalking().clear();
        }

        // Apply hook from context and short-circuit if necessary.
        if (!combat.onCombatHook(reached)) {
            return false;
        }

        // Check if we've reached the target before proceeding. If we have, stop moving.
        if (!reached) {
            if (mob.getWalking().isEmpty()) {
                // We haven't reached the target, and we're stationary. Move towards interaction range.
                if (mob instanceof Player) {
                    // todo only walk to required interaction range.
                    // todo if line of sight, should only be
                    mob.getNavigator().walkTo(target, Optional.empty(), false);
                } else {
                    mob.getActions().submitIfAbsent(new PursuitAction((Npc) mob, interactionPolicy));
                }
            }
            return false;
        }

        // Reached the entity, but there's still something in the way, and our policy is close-range melee combat.
        if (interactionPolicy.getType() == InteractionType.SIZE &&
                interactionPolicy.getDistance() == 1 && !checkMeleeCollision(target)) {
            return false;
        }

        // All checks passed, launch attack if ready.
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
        // todo NPC that has drifted too far, start wandering back to home? Specialized wandering action for this?
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
        victim.animation(combat.getDefenceAnimation());
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
