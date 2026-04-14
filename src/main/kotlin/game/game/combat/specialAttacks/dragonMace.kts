package game.combat.specialAttacks

import api.combat.specialAttack.SpecialAttackHandler.attack
import io.luna.game.model.mob.block.Graphic
import io.luna.game.model.mob.combat.SpecialAttackType.DRAGON_MACE

/**
 * The dragon mace special attack animation id.
 */
val ANIMATION = 1060

/**
 * The graphic displayed when the dragon mace special attack is performed.
 */
val GRAPHIC = Graphic(251, 100, 0)

attack(type = DRAGON_MACE,
       drain = 25,
       attackBonus = 0.25,
       damageBonus = 0.50) {

    attack { melee(ANIMATION) }

    launched { attacker.graphic(GRAPHIC) }
}