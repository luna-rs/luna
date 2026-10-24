package game.combat.specialAttacks

import api.combat.specialAttack.SpecialAttackHandler.attack
import io.luna.game.model.mob.block.Graphic
import io.luna.game.model.mob.combat.SpecialAttackType.DRAGON_DAGGER
import io.luna.game.model.mob.combat.damage.CombatDamageRequest
import io.luna.game.model.mob.combat.damage.CombatDamageAction
import io.luna.game.model.mob.combat.damage.CombatDamageType

/**
 * The dragon dagger special attack animation id.
 */
val ANIMATION = 1062

/**
 * The graphic displayed when the dragon dagger special attack is performed.
 */
val GRAPHIC = Graphic(252, 100, 0)

/**
 * Puncture increases each strike's maximum hit by 15 percent before its independent damage roll.
 */
val MAX_HIT_BONUS = 0.15

attack(type = DRAGON_DAGGER,
       drain = 25,
       attackBonus = 0.25,
       strengthBonus = MAX_HIT_BONUS) {

    // Use the dragon dagger special attack animation.
    attack { melee(ANIMATION) }


    // Resolve and apply the second strike, apply graphics.
    launched {
        attacker.graphic(GRAPHIC)

        val secondHit = CombatDamageRequest.builder(attacker, victim, CombatDamageType.MELEE)
            .setPercentBonusStrength(strengthBonus)
            .setFlatBonusAccuracy(attackBonus)
            .build()
            .resolve()

        // Use normal delivery so defence hooks, remaining health, and retaliation also apply to the second hit.
        victim.submitAction(CombatDamageAction(secondHit, attack, false))
        damage
    }
}
