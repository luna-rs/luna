package io.luna.game.model.mob.combat.attack;

import io.luna.game.model.mob.Mob;
import io.luna.game.model.mob.block.Animation;
import io.luna.game.model.mob.combat.damage.CombatDamage;
import io.luna.game.model.mob.combat.damage.CombatDamageAction;
import io.luna.game.model.mob.combat.damage.CombatDamageType;
import io.luna.game.model.mob.interact.InteractionPolicy;
import io.luna.game.model.mob.interact.InteractionType;

/**
 * A {@link CombatAttack} implementation for melee combat.
 * <p>
 * This attack plays the attacker's melee animation immediately, triggers the victim's defence animation, and queues
 * the prepared damage for application without projectile timing.
 * <p>
 * The base damage for the primary target is calculated as {@link CombatDamageType#MELEE}.
 *
 * @param <T> The concrete {@link Mob} type performing the melee attack.
 * @author lare96
 */
public class MeleeCombatAttack<T extends Mob> extends CombatAttack<T> {

    /**
     * The attack animation to play for this melee hit.
     */
    protected final Animation animation;

    /**
     * Creates a new {@link MeleeCombatAttack}.
     *
     * @param attacker The mob performing the melee attack.
     * @param victim The mob receiving the melee attack.
     * @param animation The melee attack animation to play.
     * @param delay The attack delay, in ticks, applied after execution.
     */
    public MeleeCombatAttack(T attacker, Mob victim, Animation animation, int range, int delay) {
        super(attacker, victim, new InteractionPolicy(InteractionType.SIZE, range), delay);
        this.animation = animation;
    }

    @Override
    public void attack() {
        if(animation != null) {
            attacker.animation(animation);
        }
        victim.animation(victim.getCombat().getDefenceAnimation());
        victim.submitAction(new CombatDamageAction(nextDamage, false));
    }

    @Override
    public CombatDamage calculateDamage(Mob other) {
        return CombatDamage.computed(attacker, other, CombatDamageType.MELEE);
    }
}