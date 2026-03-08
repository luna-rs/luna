package io.luna.game.model.mob.combat;

import com.google.common.base.Stopwatch;
import engine.combat.prayer.CombatPrayerSet;
import io.luna.game.model.mob.Mob;
import io.luna.game.model.mob.Player;

public final class CombatContext {
    private final Mob mob;
    private final CombatDamageStack damageStack;
    private final CombatPrayerSet prayers;
    private final CombatWeapon weapon;
    private final CombatSpecialBar specialBar;
    private final Stopwatch combatTimer = Stopwatch.createUnstarted();
    private Mob target;
    private int attackDelay;

    public CombatContext(Mob mob) {
        this.mob = mob;
        damageStack = new CombatDamageStack(mob);
        prayers = new CombatPrayerSet(mob);
        if (mob instanceof Player) {
            Player player = (Player) mob;
            weapon = new CombatWeapon(player);
            specialBar = new CombatSpecialBar(player, this);
        } else {
            weapon = null;
            specialBar = null;
        }
    }

    public void attack(Mob enemy) {
        if (mob instanceof Player) {
            Player player = (Player) mob;
            if (!player.getControllers().checkCanFight(enemy)) {
                return;
            }
        }

        target = enemy;
        if (!mob.getActions().contains(CombatAction.class)) {
            mob.submitAction(new CombatAction(mob));
        }
    }

    CombatAttack generateAttack(Mob target) {
        return null;
    }

    public int decrementAttackDelay() {
        return attackDelay--;
    }

    public void resetAttackDelay() {
        attackDelay = weapon.getStyleDef().getSpeed(); // ???
    }

    public void resetCombatTimer() {
        combatTimer.reset().start();
    }

    public void stopCombatTimer() {
        combatTimer.stop();
    }

    public boolean inCombat() {
        // TODO Is 15 seconds correct?
        return combatTimer.isRunning() && combatTimer.elapsed().toSeconds() <= 15;
    }

    public int getAttackDelay() {
        return attackDelay;
    }

    public CombatDamageStack getDamageStack() {
        return damageStack;
    }

    public CombatPrayerSet getPrayers() {
        return prayers;
    }

    public CombatWeapon getWeapon() {
        return weapon;
    }

    public CombatSpecialBar getSpecialBar() {
        return specialBar;
    }

    public Mob getTarget() {
        return target;
    }
}
