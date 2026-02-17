package game.skill.firemaking

import api.predef.*
import io.luna.game.model.mob.Player
import game.skill.Skills
import io.luna.Luna

/**
 * Holds constants and useful global functions related to Firemaking.
 *
 * @author lare96
 */
object Firemaking {

    /**
     * The tinderbox item ID.
     */
    const val TINDERBOX = 590

    /**
     * The fire object ID.
     */
    const val FIRE_OBJECT = 2732

    /**
     * The ashes item ID.
     */
    const val ASHES = 592

    /**
     * Jagex have stated that log type does not have an effect on burn times, so we use a random time between 45s
     * and 2m.
     */
    val BURN_TIME = 75..200

    /**
     * Computes the amount of ticks required to light [log].
     */
    fun computeLightDelay(plr: Player, log: Log): Int {
        val max = Luna.settings().skills().maxFiremakingLightTicks()
        var ticks = 1
        repeat(max) { // Loop until successful. User-defined maximum light duration.
            if (Skills.success(log.chance, plr.firemaking.level)) {
                return ticks
            }
            ticks++
        }
        return ticks.coerceAtMost(max)
    }
}