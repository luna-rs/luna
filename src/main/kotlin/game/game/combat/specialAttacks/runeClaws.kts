package game.combat.specialAttacks

import api.combat.specialAttack.SpecialAttackHandler.attack
import io.luna.game.model.mob.block.Graphic
import io.luna.game.model.mob.combat.SpecialAttackType.RUNE_CLAWS

/**
 * The rune claws special attack animation id.
 */
val ANIMATION = 923

/**
 * The graphic displayed when the rune claws special attack is performed.
 */
val GRAPHIC = Graphic(274, 100, 0)

attack(type = RUNE_CLAWS,
       drain = 25,
       attackBonus = 0.10,
       strengthBonus = 0.10) {

    attack { melee(ANIMATION) }

    launched { attacker.graphic(GRAPHIC); damage }
}