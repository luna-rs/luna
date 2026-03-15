package io.luna.game.model.mob.combat;

import io.luna.game.action.Action;
import io.luna.game.action.ActionType;
import io.luna.game.model.mob.Mob;

/**
 * An {@link Action} that advances and tracks a mob's combat attack delay.
 * <p>
 * This action runs once per tick while the owning mob remains in combat. Its primary responsibility is to decrement
 * the remaining attack delay stored in the mob's {@link CombatContext} and mark the combat state as ready once the
 * delay has expired.
 *
 * @author lare96
 */
public final class CombatDelayAction extends Action<Mob> {

    /**
     * The combat context for the owning mob.
     */
    private final CombatContext combat;

    /**
     * Indicates whether the owning mob may currently perform an attack.
     * <p>
     * This starts as {@code true} so a mob can attack immediately when combat begins, and is set back to {@code true}
     * once the delay reaches zero.
     */
    private boolean ready = true;

    /**
     * Creates a new combat delay action for {@code mob}.
     *
     * @param mob The mob whose attack delay will be processed.
     */
    public CombatDelayAction(Mob mob) {
        super(mob, ActionType.SOFT, false, 1);
        combat = mob.getCombat();
    }

    @Override
    public boolean run() {
        if (combat.decrementAttackDelay() < 1) {
            ready = true;
        }
        return !combat.inCombat();
    }

    /**
     * Determines whether the owning mob is ready to attack.
     *
     * @return {@code true} if an attack may be performed, otherwise {@code false}.
     */
    public boolean isReady() {
        return ready;
    }

    /**
     * Sets whether the owning mob is ready to attack.
     *
     * @param ready {@code true} if an attack may be performed, otherwise {@code false}.
     */
    public void setReady(boolean ready) {
        this.ready = ready;
    }
}