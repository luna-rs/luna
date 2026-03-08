package io.luna.game.model.mob.combat;

import io.luna.game.model.EntityState;
import io.luna.game.model.World;
import io.luna.game.model.mob.Mob;
import io.luna.game.task.Task;

public class CombatAttack {

    private final Mob attacker;
    private final Mob target;
    private final int delay;

    public CombatAttack(Mob attacker, Mob target, int delay) {
        this.attacker = attacker;
        this.target = target;
        this.delay = delay;
    }

    public final void launchAndApply() {
        // TODO no..?
        World world = attacker.getWorld();
        onLaunch();
        world.schedule(new Task(delay) {
            @Override
            protected void execute() {
                if (attacker.getState() != EntityState.INACTIVE &&
                        target.getState() != EntityState.INACTIVE &&
                        !target.isPendingPlacement()) {
                    onApply();
                    CombatDamage damage = null; // TODO ????
                    if (damage != null) {
                        target.damage(damage.getAmount(), damage.getHitType());
                    }
                }
                cancel();
            }
        });
    }

    void onLaunch() {
        // hit was launched from attacker
    }

    void onApply() {
        // hit was applied to target
    }

    public final int getDelay() {
        return delay;
    }
}
