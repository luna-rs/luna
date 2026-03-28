package io.luna.game.model.mob.wandering;

import io.luna.game.action.Action;
import io.luna.game.action.ActionType;
import io.luna.game.model.mob.Mob;

/**
 * An {@link Action} that drives passive wandering behaviour for a {@link Mob}.
 * <p>
 * Wandering actions are processed on a fixed delay and repeatedly attempt to move the mob only when it is idle. A mob
 * will only wander if it is not in combat, has no queued walking steps, is not currently interacting, and is
 * not locked.
 * <p>
 * All wandering actions use {@link ActionType#SOFT}, execute instantly on the first cycle, and then repeat every
 * {@code 3} ticks.
 *
 * @author lare96
 */
public abstract class WanderingAction extends Action<Mob> {

    /**
     * The configured wandering frequency for this action.
     */
    protected final WanderingFrequency frequency;

    /**
     * Creates a new {@link WanderingAction}.
     *
     * @param mob The mob assigned to this wandering action.
     * @param frequency The wandering frequency that controls how this action should behave.
     */
    public WanderingAction(Mob mob, WanderingFrequency frequency) {
        super(mob, ActionType.SOFT, true, 3);
        this.frequency = frequency;
    }

    @Override
    public final void onSubmit() {
        if (mob.isWandering()) {
            // Mob is already wandering.
            interrupt();
        } else {
            mob.setWandering(true);
        }
    }

    @Override
    public final boolean run() {
        if(!mob.isWandering()) {
            return true;
        } else if (!mob.getCombat().inCombat() && mob.getWalking().isEmpty() &&
                mob.getInteractingWith() == null && !mob.isLocked()) {
            wander();
        }
        return false;
    }

    @Override
    public final void onFinished() {
        // Reset wandering flag so we can schedule again in the future.
        mob.setWandering(false);
    }

    /**
     * Performs one wandering step or decision for the assigned mob.
     * <p>
     * Implementations define the specific wandering behaviour, such as random step selection, bounded roaming, or
     * path-based idle movement.
     */
    public abstract void wander();
}