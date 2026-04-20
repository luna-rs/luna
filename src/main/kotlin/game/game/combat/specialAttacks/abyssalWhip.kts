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

attack(type = ABYSSAL_WHIP,
       drain = 50,
       attackBonus = 0.25) {

    arrived {
        // Attack has landed, play tentacle spotanim, siphon run energy.
        victim.graphic(GRAPHIC)
        if (victim is Player) {
            val removeEnergy = floor(victim.runEnergy * 0.10)
            if (removeEnergy > 0) {
                victim.runEnergy -= removeEnergy
                attacker.runEnergy += removeEnergy
            }
        }
    }
}