package io.luna.game.action.impl;

import io.luna.game.action.Action;
import io.luna.game.action.ActionType;
import io.luna.game.model.Position;
import io.luna.game.model.mob.Npc;

/**
 * An action that makes an {@link Npc} retreat back toward its base position.
 * <p>
 * This action is intended to pull an NPC back toward its spawn area after it has moved too far away. Once the NPC
 * returns within an acceptable distance of its base position, the action completes.
 *
 * @author lare96
 */
public final class NpcRetreatAction extends Action<Npc> {

    /**
     * Determines the reason or rule used to trigger retreat behavior.
     */
    public enum RetreatPolicy {

        /**
         * Retreat when the NPC falls below a configured health threshold.
         */
        LOW_HEALTH,

        /**
         * Retreat when the NPC has moved too far away from its base position.
         */
        DISTANCE,

        /**
         * No retreat behavior is applied.
         */
        NONE
    }

    /**
     * Creates a new {@link NpcRetreatAction}.
     *
     * @param mob The NPC performing the retreat.
     */
    public NpcRetreatAction(Npc mob) {
        super(mob, ActionType.STRONG, true, 1);
    }

    @Override
    public boolean run() {
        Position spawnLocation = mob.getBasePosition();

        if (mob.getPosition().isWithinDistance(spawnLocation, Position.VIEWING_DISTANCE / 2)) {
            return true;
        }

        if (!mob.getWalking().isEmpty()) {
            mob.getNavigator().walk(spawnLocation, true);
        }
        return false;
    }
}