package game.combat.specialAttacks

import api.combat.specialAttack.SpecialAttackHandler.attack
import engine.combat.status.BasicStatusEffect
import engine.combat.status.StatusEffectType
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Graphic
import io.luna.game.model.mob.combat.SpecialAttackType.DRAGON_SCIMITAR
import kotlin.time.Duration.Companion.minutes

/**
 * The dragon scimitar special attack animation id.
 */
val ANIMATION = 1872

/**
 * The graphic displayed when the dragon scimitar special attack is performed.
 */
val GRAPHIC = Graphic(347, 100, 0)

/**
 * Dragon scimitar special attack status effect.
 *
 * This marks a player as prayer-disabled for five minutes and persists across logout.
 *
 * @param player The player affected by the dragon scimitar special attack.
 * @author lare96
 */
class DragonScimitarStatusEffect(player: Player) :
    BasicStatusEffect<Player>(player,
                              type = StatusEffectType.DRAGON_SCIMITAR,
                              duration = 5.minutes,
                              startMsg = "Your prayers have been disabled!",
                              endMsg = "Your prayers have been enabled.",
                              persistent = true)

attack(type = DRAGON_SCIMITAR,
       drain = 55) {

    attack { melee(ANIMATION) }

    launched { attacker.graphic(GRAPHIC); damage }

    arrived {
        if (victim is Player) {
            victim.status.add(DragonScimitarStatusEffect(victim))
        }
    }
}