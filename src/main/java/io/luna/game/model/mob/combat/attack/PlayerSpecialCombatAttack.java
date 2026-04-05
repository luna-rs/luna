package io.luna.game.model.mob.combat.attack;

import io.luna.game.model.mob.Mob;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.combat.damage.CombatDamage;
import io.luna.game.model.mob.interact.InteractionPolicy;

/**
 *
 *
 * @author lare96
 */
public final class PlayerSpecialCombatAttack extends CombatAttack<Player> {

    /**
     * Creates a new {@link CombatAttack}.
     *
     * @param attacker The mob performing the attack.
     * @param victim The mob receiving the attack.
     * @param interactionPolicy The interaction policy that must be satisfied in order for this attack to proceed.
     * @param delay The attack delay, in ticks, applied after execution.
     */
    public PlayerSpecialCombatAttack(Player attacker, Mob victim, InteractionPolicy interactionPolicy, int delay) {
        super(attacker, victim, interactionPolicy, delay);
    }

    @Override
    public void attack() {

    }

    @Override
    public CombatDamage calculateDamage(Mob other) {
        return null;
    }
}
