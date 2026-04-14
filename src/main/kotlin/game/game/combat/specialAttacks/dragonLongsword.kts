package game.combat.specialAttacks

import api.combat.specialAttack.SpecialAttackHandler.attack
import io.luna.game.model.mob.block.Graphic
import io.luna.game.model.mob.combat.SpecialAttackType.DRAGON_LONGSWORD

/**
 * The dragon mace special attack animation id.
 */
val ANIMATION = 1058

/**
 * The graphic displayed when the dragon mace special attack is performed.
 */
val GRAPHIC = Graphic(248, 100, 0)

attack(type = DRAGON_LONGSWORD,
       drain = 25,
       damageBonus = 0.15) {

    attack { melee(ANIMATION) }

    launched { attacker.graphic(GRAPHIC) }
}