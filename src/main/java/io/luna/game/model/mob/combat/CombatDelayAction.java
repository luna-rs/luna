package io.luna.game.model.mob.combat;

import io.luna.game.action.Action;
import io.luna.game.action.ActionType;
import io.luna.game.model.mob.Mob;

/**
 * An {@link Action} that tracks and advances a mob's combat attack delay.
 * <p>
 * This action runs once per tick while the owning mob remains in combat. It is responsible for counting down the
 * remaining attack delay and marking the mob as ready to attack once that delay expires.
 *
 * @author lare96
 */
public final class CombatDelayAction extends Action<Mob> {


    // todo this should always be running, if it stops and gets restarted mid-ish combat could result in weird delays?

    /**
     * Indicates whether the owning mob may currently perform an attack.
     * <p>
     * This starts as {@code true} so combat can begin immediately, and becomes @code true} again once the current
     * attack delay has elapsed.
     */
    private boolean ready = true;

    /**
     * The number of ticks remaining before another attack may be performed.
     */
    private int attackDelay;

    /**
     * Creates a new {@link CombatDelayAction} for the specified mob.
     *
     * @param mob The mob whose combat delay will be processed.
     */
    public CombatDelayAction(Mob mob) {
        super(mob, ActionType.SOFT, false, 1);
    }

    @Override
    public void onProcess() {
        // Decrement the attack timer during the process phase so attack readiness is resolved before any combat
        // execution happens this tick.
        if (attackDelay-- < 1) {
            ready = true;
        }
    }

    @Override
    public boolean run() {
        return !mob.getCombat().inCombat();
    }

    /**
     * Resets the attack delay and marks the owning mob as not ready to attack until the supplied delay has elapsed.
     *
     * @param delay The new attack delay, in ticks.
     */
    public void reset(int delay) {
        ready = false;
        attackDelay = delay;
    }

    /**
     * Determines whether the owning mob is currently ready to attack.
     *
     * @return {@code true} if an attack may be performed, otherwise {@code false}.
     */
    public boolean isReady() {
        return ready;
    }

    /**
     * Sets whether the owning mob is currently ready to attack.
     *
     * @param ready {@code true} if an attack may be performed, otherwise {@code false}.
     */
    public void setReady(boolean ready) {
        this.ready = ready;
    }
}