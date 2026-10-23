package game.combat.specialAttacks

import api.combat.specialAttack.SpecialAttackHandler.attack
import engine.combat.status.BasicStatusEffect
import engine.combat.status.StatusEffectType
import engine.combat.prayer.CombatPrayer
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Graphic
import io.luna.game.model.mob.combat.SpecialAttackType.DRAGON_SCIMITAR
import kotlin.time.Duration.Companion.seconds

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
 * An accurate hit removes active protection prayers and prevents their activation for five seconds. Other prayers
 * remain available, and the status persists across logout.
 *
 * @param player The player affected by the dragon scimitar special attack.
 * @author lare96
 */
class DragonScimitarStatusEffect(player: Player) :
    BasicStatusEffect<Player>(player,
                              type = StatusEffectType.DRAGON_SCIMITAR,
                              duration = 5.seconds,
                              startMsg = "Your protection prayers have been disabled!",
                              endMsg = "Your protection prayers have been enabled.",
                              persistent = true)

attack(type = DRAGON_SCIMITAR,
       drain = 55) {

    attack { melee(ANIMATION) }

    launched { attacker.graphic(GRAPHIC); damage }

    arrived {
        if (victim is Player && damage.isAccurate) {
            victim.combat.prayers.deactivate(CombatPrayer.PROTECT_FROM_MELEE, true)
            victim.combat.prayers.deactivate(CombatPrayer.PROTECT_FROM_MISSILES, true)
            victim.combat.prayers.deactivate(CombatPrayer.PROTECT_FROM_MAGIC, true)
            victim.status.add(DragonScimitarStatusEffect(victim))
        }
    }
}
