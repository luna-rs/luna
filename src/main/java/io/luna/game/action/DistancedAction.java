package io.luna.game.action;

import io.luna.game.model.Position;
import io.luna.game.model.mobile.Mob;

/**
 * An {@link Action} implementation that executes when the mob is within a certain range of a position.
 *
 * @author lare96 <http://github.org/lare96>
 */
public abstract class DistancedAction<T extends Mob> extends Action<T> {

    /**
     * The position.
     */
    private final Position position;

    /**
     * The radius to the position.
     */
    private final int radius;

    /**
     * If the action should be interrupted.
     */
    private final boolean interrupt;

    /**
     * Creates a new {@link DistancedAction}.
     *
     * @param mob The mob this action is for.
     * @param position The position.
     * @param radius The radius to the position.
     * @param interrupt If the action should be interrupted.
     */
    public DistancedAction(T mob, Position position, int radius, boolean interrupt) {
        super(mob, true, 1);
        this.position = position;
        this.radius = radius;
        this.interrupt = interrupt;
    }

    @Override
    protected final void call() {
        Position mobPosition = mob.getPosition();
        if (mobPosition.isWithinRadius(position, radius)) {
            execute();

            if (interrupt) {
                interrupt();
            }
        }
    }

    /**
     * Function called when the mob is within correct range of the position.
     */
    protected abstract void execute();
}
