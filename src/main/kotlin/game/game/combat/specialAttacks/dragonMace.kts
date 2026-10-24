package game.combat.specialAttacks

import api.combat.specialAttack.SpecialAttackHandler.attack
import io.luna.game.model.item.Equipment.EquipmentBonus
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

/**
 * Shatter increases the maximum hit by 50 percent before rolling, allowing every damage value up to that maximum.
 */
val MAX_HIT_BONUS = 0.50

/**
 * Shatter boosts the offensive roll by 25 percent before comparison with the target's defence.
 * It retains the attacker's selected offensive style but always checks the target's crush defence.
 */
val ATTACK_ROLL_MULTIPLIER = 1.25

attack(type = DRAGON_MACE,
       drain = 25,
       meleeDefenceBonus = EquipmentBonus.CRUSH_DEFENCE,
       attackRollMultiplier = ATTACK_ROLL_MULTIPLIER,
       strengthBonus = MAX_HIT_BONUS) {

    attack { melee(ANIMATION) }

    launched { attacker.graphic(GRAPHIC); damage }
}
