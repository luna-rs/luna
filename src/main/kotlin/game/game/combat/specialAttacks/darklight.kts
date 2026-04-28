package game.combat.specialAttacks

import api.combat.specialAttack.SpecialAttackHandler.attack
import api.predef.*
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.block.Graphic
import io.luna.game.model.mob.combat.SpecialAttackType.DARKLIGHT
import kotlin.math.floor

/**
 * The animation played when Darklight's special attack is performed.
 */
val ANIMATION = 2890

/**
 * The graphic displayed on the attacker when Darklight's special attack is launched.
 */
val GRAPHIC = Graphic(483, 0, 0)

attack(type = DARKLIGHT, drain = 50) {
    attack { melee(ANIMATION) }

    launched { attacker.graphic(GRAPHIC); damage }

    arrived {
        if (damage.amount.isPresent) {
            // If attack landed reduce victim's stats by 5% (double if used against demons).
            val reduceBy = if (victim is Npc && victim.def().name.contains("demon", true)) 0.10 else 0.05
            val attackWeaken = floor(victim.attack.staticLevel * reduceBy).toInt()
            val strengthWeaken = floor(victim.strength.staticLevel * reduceBy).toInt()
            val defenceWeaken = floor(victim.defence.staticLevel * reduceBy).toInt()
            victim.attack.weaken(attackWeaken)
            victim.strength.weaken(strengthWeaken)
            victim.defence.weaken(defenceWeaken)
        }
    }
}