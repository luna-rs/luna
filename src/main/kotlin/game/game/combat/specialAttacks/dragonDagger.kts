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

/*
 * Registers the dragon dagger special attack.
 *
 * This special attack drains 25% special attack energy, uses a 25% accuracy bonus, and applies a 15% damage bonus
 * to the manually resolved hit performed during the launch stage.
 */
attack(type = DRAGON_DAGGER,
       drain = 25,
       attackBonus = 0.25,
       damageBonus = 0.15) {

    // Starts the special attack using the dragon dagger special attack animation.
    attack { melee(ANIMATION) }

    /*
     * Resolves and applies the second strike.
     *
     * The hit is resolved as melee damage using the configured special attack accuracy and damage bonuses, then
     * applied directly to the victim.
     */
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