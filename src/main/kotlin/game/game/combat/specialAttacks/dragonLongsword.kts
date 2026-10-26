package game.combat.specialAttacks

import api.combat.specialAttack.SpecialAttackHandler.attack
import game.player.Sound
import io.luna.game.model.item.Equipment.EquipmentBonus
import io.luna.game.model.mob.block.Graphic
import io.luna.game.model.mob.combat.SpecialAttackType.DRAGON_LONGSWORD

/**
 * The dragon longsword special attack animation id.
 */
val ANIMATION = 1058

/**
 * The graphic displayed when the dragon longsword special attack is performed.
 */
val GRAPHIC = Graphic(248, 100, 0)

/**
 * Cleave increases the maximum hit by 25 percent before damage is rolled, rather than scaling the rolled damage.
 * Its accuracy uses the selected offensive style against the target's slash defence.
 */
val MAX_HIT_BONUS = 0.25

/**
 * Configures the dragon longsword special attack, including its verified Lost City launch sound.
 *
 * @author lare96
 */
attack(type = DRAGON_LONGSWORD,
       drain = 25,
       meleeDefenceBonus = EquipmentBonus.SLASH_DEFENCE,
       strengthBonus = MAX_HIT_BONUS) {

    launchSound = Sound.CLEAVE

    attack { melee(ANIMATION) }

    launched { attacker.graphic(GRAPHIC); damage }
}
