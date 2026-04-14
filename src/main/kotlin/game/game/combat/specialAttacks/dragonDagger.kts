package game.combat.specialAttacks

import api.combat.specialAttack.SpecialAttackHandler.attack
import io.luna.game.model.mob.block.Graphic
import io.luna.game.model.mob.combat.SpecialAttackType.DRAGON_DAGGER
import io.luna.game.model.mob.combat.damage.CombatDamageRequest
import io.luna.game.model.mob.combat.damage.CombatDamageType

/**
 * The dragon dagger special attack animation id.
 */
val ANIMATION = 1062

/**
 * The graphic displayed when the dragon dagger special attack is performed.
 */
val GRAPHIC = Graphic(252, 100, 0)

attack(type = DRAGON_DAGGER,
       drain = 25,
       attackBonus = 0.25,
       damageBonus = 0.15) {

    // Use the dragon dagger special attack animation.
    attack { melee(ANIMATION) }


    // Resolve and apply the second strike, apply graphics.
    launched {
        attacker.graphic(GRAPHIC)

        val damage = CombatDamageRequest.builder(attacker, victim, CombatDamageType.MELEE)
            .setPercentBonusDamage(damageBonus)
            .setFlatBonusAccuracy(attackBonus)
            .build()
            .resolve()

        damage.apply()
    }
}