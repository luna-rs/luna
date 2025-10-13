package world.player.skill.firemaking

import api.predef.*
import io.luna.game.model.mob.Player
import world.player.skill.Skills

/**
 * Holds constants and useful global functions related to Firemaking.
 */
object Firemaking {

    /**
     * The maximum amount of ticks the player can wait before a log will be lit.
     */
    const val MAXIMUM_LIGHT_DURATION = 10

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
        var ticks = 1
        repeat(MAXIMUM_LIGHT_DURATION) { // Loop until successful. User-defined maximum light duration.
            if (Skills.success(log.chance, plr.firemaking.level)) {
                return ticks
            }
            ticks++
        }
        return ticks.coerceAtMost(MAXIMUM_LIGHT_DURATION)
    }
}