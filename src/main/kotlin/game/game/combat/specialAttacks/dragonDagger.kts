package game.combat.specialAttacks

import api.combat.specialAttack.SpecialAttackHandler.attack
import io.luna.game.action.Action
import io.luna.game.action.ActionType
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Graphic
import io.luna.game.model.mob.combat.SpecialAttackType.DRAGON_DAGGER
import io.luna.game.model.mob.combat.attack.CombatAttack
import io.luna.game.model.mob.combat.damage.CombatDamage
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

/**
 * Queues Puncture's second strike, requiring a living, active attacker when the strike executes.
 * Successful delivery still uses normal defence hooks, damage attribution, and retaliation.
 *
 * @param damage The independently resolved second hit.
 * @param source The original special attack providing the attacker and damage callbacks.
 */
fun queueSecondHit(damage: CombatDamage, source: CombatAttack<Player>) {
    damage.victim.submitAction(object : Action<Mob>(damage.victim, ActionType.SOFT, false, 1) {
        override fun run(): Boolean {
            if (source.attacker.isAlive) {
                CombatDamageAction(damage, source, true).run()
            }
            return true
        }
    })
}

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
        queueSecondHit(secondHit, requireNotNull(attack))
        damage
    }
}
