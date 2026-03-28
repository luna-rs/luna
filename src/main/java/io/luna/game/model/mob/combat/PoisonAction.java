package io.luna.game.model.mob.combat;

import io.luna.game.action.Action;
import io.luna.game.action.ActionType;
import io.luna.game.model.mob.Mob;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.block.Hit.HitType;
import io.luna.game.model.mob.combat.state.CombatContext;

/**
 * An action that applies periodic poison damage to a {@link Mob}.
 * <p>
 * Poison damage is processed on a fixed interval and continues until the mob's poison severity is reduced to zero or
 * below. Each execution lowers the stored poison severity through {@link CombatContext#decrementPoisonSeverity()}
 * and, if poison is still active, applies poison damage based on the remaining severity.
 *
 * @author lare96
 */
public final class PoisonAction extends Action<Mob> {

    /**
     * The combat context for the poisoned mob.
     */
    private final CombatContext<?> combat;

    /**
     * Creates a new {@link PoisonAction}.
     *
     * @param mob The poisoned mob.
     * @param instant {@code true} if the action should begin immediately, otherwise {@code false}.
     */
    public PoisonAction(Mob mob, boolean instant) {
        super(mob, ActionType.NORMAL, instant, 30);
        combat = mob.getCombat();
    }

    @Override
    public void onSubmit() {
        if (mob instanceof Player && isInstant()) {
            ((Player) mob).sendMessage("You have been poisoned!");
        }
    }

    @Override
    public boolean run() {
        double severity = combat.decrementPoisonSeverity();
        if (severity > 0) {
            mob.damage((int) Math.floor((severity + 4.0) / 5.0), HitType.POISON);
            return false;
        }
        return true;
    }
}