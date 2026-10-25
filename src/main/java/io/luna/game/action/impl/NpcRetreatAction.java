package io.luna.game.action.impl;

import io.luna.game.action.Action;
import io.luna.game.action.ActionType;
import io.luna.game.model.Direction;
import io.luna.game.model.Position;
import io.luna.game.model.mob.Mob;
import io.luna.game.model.mob.Npc;

/**
 * Lost City's playerescape mode: flee from the attacker while staying inside the spawn-relative maximum range.
 * Combat and pursuit end before escape begins; ordinary wandering or patrol resumes when escape completes.
 *
 * @author lare96
 */
public final class NpcRetreatAction extends Action<Npc> {

    /**
     * Selects configured escape and leash rules, or an explicit content override.
     *
     * @author lare96
     */
    public enum RetreatPolicy {
        /**
         * Apply this NPC type's leash and absolute hitpoint escape threshold.
         */
        CONFIGURED,
        /**
         * Apply only this NPC type's absolute hitpoint escape threshold.
         */
        LOW_HEALTH,
        /**
         * Apply only this NPC type's combat leash.
         */
        DISTANCE,
        /**
         * Disable automatic leashing and escape for scripted encounters.
         */
        NONE
    }

    /**
     * The attacker being escaped, retained after combat clears its target.
     */
    private final Mob threat;

    /**
     * The last observed position, used to detect blocked movement rather than merely queued steps.
     */
    private Position lastPosition;

    /**
     * Consecutive escape cycles without movement.
     */
    private int stuckTicks;

    /**
     * Creates an escape from the NPC's current combat target.
     *
     * @param mob The fleeing NPC.
     */
    public NpcRetreatAction(Npc mob) {
        this(mob, mob.getCombat().getTarget());
    }

    /**
     * Creates an escape from the supplied attacker.
     *
     * @param mob The fleeing NPC.
     * @param threat The attacker to move away from.
     */
    public NpcRetreatAction(Npc mob, Mob threat) {
        super(mob, ActionType.STRONG, true, 1);
        this.threat = threat;
    }

    @Override
    public void onSubmit() {
        mob.getActions().interruptWeak();
        mob.getCombat().setTarget(null);
        mob.getCombat().setAutoRetaliateTarget(null);
        mob.getNavigator().cancel();
        mob.getWalking().clear();
        mob.interact(null);
    }

    @Override
    public boolean run() {
        Position position = mob.getPosition();
        if (!mob.isAlive() || threat == null || !threat.isAlive() || mob.getCombat().isDisabled() ||
                !position.isWithinDistance(threat.getPosition(), 25)) {
            return true;
        }
        Position spawn = mob.getBasePosition();
        int maxRange = mob.combatDef().getMovement().maxRange();
        // Lost City checks occupied rectangles here, unlike the southwest-tile limit on individual steps.
        if (rectangleDistance(position, spawn, mob.size()) > maxRange &&
                rectangleDistance(threat.getPosition(), spawn, threat.size()) > maxRange) {
            return true;
        }
        stuckTicks = position.equals(lastPosition) ? stuckTicks + 1 : 0;
        lastPosition = position;
        int dx = Math.abs(position.getX() - spawn.getX());
        int dy = Math.abs(position.getY() - spawn.getY());
        if (stuckTicks >= 5 && !(dx >= maxRange && dy >= maxRange)) {
            return true;
        }
        if (mob.isLocked() || mob.isStationary() || mob.getStatus().isImmobilized() ||
                mob.getWalking().isLocked() || !mob.getWalking().isEmpty()) {
            return false;
        }
        Direction horizontal = threat.getX() >= position.getX() ? Direction.WEST : Direction.EAST;
        Direction vertical = threat.getY() >= position.getY() ? Direction.SOUTH : Direction.NORTH;
        Direction diagonal = Direction.between(position,
                position.translate(horizontal.getTranslateX(), vertical.getTranslateY()));
        if (!tryStep(diagonal, spawn, maxRange) && !tryStep(horizontal, spawn, maxRange)) {
            tryStep(vertical, spawn, maxRange);
        }
        return false;
    }

    @Override
    public void onFinished() {
        mob.getWalking().clear();
        mob.interact(null);
    }

    /**
     * Attempts a step only if it remains inside the escape bounds and passes the NPC's collision rules.
     */
    private boolean tryStep(Direction direction, Position spawn, int maxRange) {
        return mob.getPosition().translate(1, direction).isWithinDistance(spawn, maxRange) &&
                mob.getNavigator().step(direction);
    }

    /**
     * Returns the Chebyshev gap between equally sized occupied squares at two southwest positions.
     */
    private static int rectangleDistance(Position position, Position spawn, int size) {
        return Math.max(0, Math.max(Math.abs(position.getX() - spawn.getX()),
                Math.abs(position.getY() - spawn.getY())) - Math.max(0, size - 1));
    }
}
