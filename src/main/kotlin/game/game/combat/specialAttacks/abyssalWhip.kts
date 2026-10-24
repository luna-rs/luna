package game.combat.specialAttacks

import api.combat.specialAttack.SpecialAttackHandler.attack
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Graphic
import io.luna.game.model.mob.combat.SpecialAttackType.ABYSSAL_WHIP
import kotlin.math.floor

/**
 * The graphic applied when the attack lands.
 */
val GRAPHIC = Graphic(341, 100, 0)

/**
 * Energy Drain scales the offensive roll to 125 percent before comparison with the target's defence.
 */
val ATTACK_ROLL_MULTIPLIER = 1.25

/**
 * Calculates ten percent of the target's run energy, rounded down to hundredths of a percentage point.
 * Luna stores energy on a 0–100 scale; the reference uses 0–10000 integer units and divides by ten.
 * For example, 9 percent energy yields a 0.9 percentage-point drain instead of being rounded to zero.
 */
fun calculateEnergyDrain(energy: Double): Double = floor(energy * 10.0) / 100.0

attack(type = ABYSSAL_WHIP,
       drain = 50,
       attackRollMultiplier = ATTACK_ROLL_MULTIPLIER) {

    arrived {
        // Show the impact graphic on arrival, but only siphon energy when the accuracy roll succeeded.
        victim.graphic(GRAPHIC)
        if (victim is Player && damage.isAccurate) {
            val removeEnergy = calculateEnergyDrain(victim.runEnergy)
            if (removeEnergy > 0) {
                victim.runEnergy -= removeEnergy
                attacker.runEnergy += removeEnergy
            }
        }
    }
}
