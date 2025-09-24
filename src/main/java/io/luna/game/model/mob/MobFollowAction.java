package io.luna.game.model.mob;

import io.luna.game.action.Action;
import io.luna.game.action.ActionType;
import io.luna.game.model.Position;

/**
 * An {@link Action} implementation that makes a mob follow the target mob.
 *
 * @author lare96
 */
public final class MobFollowAction extends Action<Mob> {

    /**
     * The target mob.
     */
    private final Mob target;

    /**
     * The last position of this mob.
     */
    private Position lastPosition;

    /**
     * Creates a new {@link MobFollowAction}.
     *
     * @param mob The target mob.
     * @param target The last position of this mob.
     */
    public MobFollowAction(Mob mob, Mob target) {
        super(mob, ActionType.WEAK);
        this.target = target;
    }

    @Override
    public boolean run() {
        int distance = mob.getPosition().getEuclideanDistance(target.getPosition());
        if (distance >= 15) {
            return true;
        }
        mob.interact(target);
        if (target.getPosition().equals(lastPosition)) {
            return false;
        }
        if (mob.getPosition() == target.getPosition()) {
            mob.walking.walkRandomDirection();
        } else {
            mob.walking.walkBehind(target);
            lastPosition = target.getPosition();
        }
        return false;
    }

    @Override
    public void onFinished() {
        mob.interact(null);
    }
}
