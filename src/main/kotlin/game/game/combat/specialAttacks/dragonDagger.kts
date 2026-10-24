package game.combat.specialAttacks

import api.combat.specialAttack.SpecialAttackHandler.attack
import io.luna.game.action.Action
import io.luna.game.action.ActionType
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Graphic
import io.luna.game.model.mob.combat.CombatFormula
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
 * Puncture boosts each strike's offensive roll by 15 percent before comparison with the target's defence.
 */
val ATTACK_ROLL_MULTIPLIER = 1.15

/**
 * Queues Puncture's second strike, requiring a living, active attacker when the strike executes.
 * Successful delivery still uses normal defence hooks, damage attribution, and retaliation.
 * Launch callbacks run before the primary melee hit is queued. This strike therefore waits two ticks instead of the
 * primary hit's one tick, ensuring it lands one tick later even though it enters the victim's action queue first.
 *
 * @param damage The independently resolved second hit.
 * @param source The original special attack providing the attacker and damage callbacks.
 */
fun queueSecondHit(damage: CombatDamage, source: CombatAttack<Player>) {
    damage.victim.submitAction(object : Action<Mob>(damage.victim, ActionType.SOFT, false, 2) {
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
       attackRollMultiplier = ATTACK_ROLL_MULTIPLIER,
       strengthBonus = MAX_HIT_BONUS) {

    // Use the dragon dagger special attack animation.
    attack { melee(ANIMATION) }


    // Resolve and apply the second strike, apply graphics.
    launched {
        attacker.graphic(GRAPHIC)

        val secondHit = CombatDamageRequest.builder(attacker, victim, CombatDamageType.MELEE)
            .setPercentBonusStrength(strengthBonus)
            .setBaseAccuracy(CombatFormula.calculateHitChance(attacker, victim, CombatDamageType.MELEE, attackRollPercent))
            .build()
            .resolve()

        // Use normal delivery so defence hooks, remaining health, and retaliation also apply to the second hit.
        queueSecondHit(secondHit, requireNotNull(attack))
        damage
    }
}
