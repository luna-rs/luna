package game.combat.specialAttacks

import api.combat.specialAttack.SpecialAttackHandler.attack
import game.player.Sound
import io.luna.game.model.item.Equipment.EquipmentBonus
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
 * Both strikes retain the selected offensive style but always check the target's slash defence.
 */
val ATTACK_ROLL_MULTIPLIER = 1.15

/**
 * Queues Puncture's second strike, requiring a living, active attacker when the strike executes.
 * Successful delivery still uses normal defence hooks, damage attribution, and retaliation.
 * The recipient's impact queue schedules this strike two world ticks after launch, one tick after the primary hit.
 * Duel settlement and life resets clear this queue, so the strike cannot survive cleanup and hit in the lobby or
 * after respawn. The deadline is independent of whether the recipient's ordinary actions ran earlier this tick.
 *
 * @param damage The independently resolved second hit.
 * @param source The original special attack providing the attacker and damage callbacks.
 */
fun queueSecondHit(damage: CombatDamage, source: CombatAttack<Player>) {
    damage.victim.combat.impacts.schedule(2, {
        if (source.attacker.isAlive) {
            damage.victim.submitAction(CombatDamageAction(damage, source, true))
        }
    }, {})
}

/**
 * Configures the dragon dagger special attack, including its verified Lost City launch sound.
 *
 * @author lare96
 */
attack(type = DRAGON_DAGGER,
       drain = 25,
       meleeDefenceBonus = EquipmentBonus.SLASH_DEFENCE,
       attackRollMultiplier = ATTACK_ROLL_MULTIPLIER,
       strengthBonus = MAX_HIT_BONUS) {

    launchSound = Sound.PUNCTURE

    // Use the dragon dagger special attack animation.
    attack { melee(ANIMATION) }

    // Resolve and apply the second strike, apply graphics.
    launched {
        attacker.graphic(GRAPHIC)

        val secondHit = CombatDamageRequest.builder(attacker, victim, CombatDamageType.MELEE)
            .setPercentBonusStrength(strengthBonus)
            .setBaseAccuracy(CombatFormula.calculateHitChance(attacker, victim, CombatDamageType.MELEE,
                attackRollPercent, meleeDefenceBonus))
            .build()
            .resolve()

        // Use normal delivery so defence hooks, remaining health, and retaliation also apply to the second hit.
        queueSecondHit(secondHit, requireNotNull(attack))
        damage
    }
}
