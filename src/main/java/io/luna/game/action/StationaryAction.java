package io.luna.game.action;

import io.luna.game.model.mob.Mob;

/**
 * An {@link Action} implementation that details actions related specifically to standing still.
 *
 * @author lare96 <http://github.org/lare96>
 */
public abstract class StationaryAction<T extends Mob> extends Action<T> {

    /**
     * Creates a new {@link StationaryAction}.
     *
     * @param mob The {@link Mob} assigned to this action.
     * @param instant If this action executes instantly.
     * @param delay The delay of this action.
     */
    public StationaryAction(T mob, boolean instant, int delay) {
        super(mob, instant, delay);
    }

    @Override
    protected final void onInit() {
        if (canInit()) {
            mob.getWalking().clear();
        } else {
            interrupt();
        }
    }

    /**
     * Returns whether or not the action can be initialized.
     *
     * @return {@code false} to interrupt the action.
     */
    protected boolean canInit() { // TODO let canproduce, etc. delegate to this
        return true;
    }
}