package io.luna.game.model.mob.combat.damage;

import io.luna.game.action.Action;
import io.luna.game.action.ActionType;
import io.luna.game.model.mob.Mob;
import io.luna.game.model.mob.combat.CombatAction;
import io.luna.game.model.mob.combat.attack.CombatAttack;

/**
 * An {@link Action} that applies a resolved {@link CombatDamage} hit to its victim and then performs auto-retaliate
 * handling for that victim.
 * <p>
 * The owning mob for this action is the damage victim. When executed, the action:
 * <ul>
 *     <li>Applies the resolved hit if the victim is still alive.</li>
 *     <li>Either immediately retaliates or stores the attacker as an auto-retaliate target, depending on the victim's
 *     current combat state.</li>
 * </ul>
 * This action completes after a single execution.
 *
 * @author lare96
 */
public final class CombatDamageAction extends Action<Mob> {

    /**
     * The mob that originally dealt the hit and may become the victim's retaliation target.
     */
    private final Mob attacker;

    /**
     * The mob receiving the hit.
     * <p>
     * This is also the owning mob for this action.
     */
    private final Mob victim;

    /**
     * The resolved combat damage to apply.
     */
    private CombatDamage damage;

    /**
     * The source of the resolved damage.
     */
    private final CombatAttack<?> source;

    /**
     * Creates a new {@link CombatDamageAction}.
     *
     * @param damage The resolved combat damage to apply.
     * @param source The source of the resolved damage.
     * @param instant {@code true} if the action should execute instantly, otherwise {@code false}.
     */
    public CombatDamageAction(CombatDamage damage, CombatAttack<?> source, boolean instant) {
        super(damage.getVictim(), ActionType.SOFT, instant, 1);
        this.damage = damage;
        this.source = source;
        attacker = damage.getAttacker();
        victim = damage.getVictim();
    }

    @Override
    public void onSubmit() {
        if (isInstant() && run()) {
            complete();
        }
    }

    @Override
    public boolean run() {
        if (victim.isAlive()) {
            victim.getCombat().onNextDefence(attacker, damage, this);

            // Apply the hit.
            if (damage != null) {
                damage.apply();
                source.onAttackArrived(damage);
            }

            // Determine whether the victim should retaliate.
            if (victim.isAlive() && victim.getCombat().isAutoRetaliate()) {
                if (victim.getCombat().getTarget() == null || !victim.getActions().contains(CombatAction.class)) {
                    victim.getCombat().attack(attacker);
                } else {
                    victim.getCombat().setAutoRetaliateTarget(attacker);
                }
            }
        }
        return true;
    }

    public CombatDamage getDamage() {
        return damage;
    }

    public void setDamage(CombatDamage damage) {
        this.damage = damage;
    }

    public CombatAttack<?> getSource() {
        return source;
    }
}