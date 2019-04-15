package io.luna.game.action;

import io.luna.game.model.Position;
import io.luna.game.model.mob.Mob;

/**
 * An {@link Action} implementation that executes when the mob is within a certain range of a position, and interrupts
 * itself afterwards.
 *
 * @author lare96 <http://github.org/lare96>
 */
public abstract class DistancedAction<T extends Mob> extends RepeatingAction<T> {

    /**
     * The position.
     */
    protected final Position position;

    /**
     * The radius to the position.
     */
    protected final int radius;

    /**
     * Creates a new {@link DistancedAction}.
     *
     * @param mob The mob this action is for.
     * @param position The position.
     * @param radius The radius to the position.
     */
    public DistancedAction(T mob, Position position, int radius) {
        super(mob, true, 1);
        this.position = position;
        this.radius = radius;
    }

    @Override
    public boolean start() {
        return true;
    }

    @Override
    public final void repeat() {
        var mobPosition = mob.getPosition();
        if (mobPosition.isWithinDistance(position, radius)) {
            withinDistance();
            interrupt();
        }
    }

    /**
     * Function called every tick while the mob is within {@code radius} relative to {@code position}.
     */
    protected abstract void withinDistance();
}
