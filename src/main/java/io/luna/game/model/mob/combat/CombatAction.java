package io.luna.game.model.mob.combat;

import io.luna.game.action.Action;
import io.luna.game.action.ActionType;
import io.luna.game.model.mob.Mob;
import io.luna.game.model.mob.block.Animation;

public final class CombatAction extends Action<Mob> {

    private final CombatContext combat;

    public CombatAction(Mob mob) {
        super(mob, ActionType.WEAK, false, 1);
        combat = mob.getCombat();
    }

    @Override
    public boolean run() {
        combat.decrementAttackDelay();
        if (combat.getAttackDelay() < 1) {
            combat.resetAttackDelay();
            mob.animation(new Animation(combat.getWeapon().getStyleDef().getAnimation()));
            mob.speak("i attacked");
        }
        return false;
    }
}
