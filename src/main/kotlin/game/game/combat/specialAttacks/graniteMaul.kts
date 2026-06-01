package game.combat.specialAttacks

import api.combat.specialAttack.SpecialAttackHandler.attack
import io.luna.game.model.mob.block.Graphic
import io.luna.game.model.mob.combat.SpecialAttackType.*

/**
 * The granite maul special attack animation id.
 */
val ANIMATION = 1667

/**
 * The graphic displayed when the granite maul special attack is performed.
 */
val GRAPHIC = Graphic(340, 100, 0)

attack(type = GRANITE_MAUL,
       drain = 50,
       instant = true) {

    // Use the granite maul special attack animation.
    attack { melee(ANIMATION) }

    // Apply graphics.
    launched {
        victim.graphic(GRAPHIC)
        damage
    }
}