package world.player.skill.firemaking

import api.predef.*
import io.luna.game.model.mob.Player

/**
 * Holds constants and useful global functions related to Firemaking.
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
     * How fast logs will be lit. Higher value = slower. Must be greater than or equal to 2.
     */
    const val BASE_LIGHT_RATE = 13

    /**
     * Jagex have stated that log type does not have an effect on burn times, so we use a random time between 45s
     * and 2m.
     */
    val BURN_TIME = 75..200

    /**
     * Computes the time it will take to light [log].
     */
    fun computeLightDelay(plr: Player, log: Log): Int {
        var baseTime = rand(BASE_LIGHT_RATE / 2, BASE_LIGHT_RATE)
        var levelFactor = plr.firemaking.level - log.level
        if (levelFactor >= 2) {
            levelFactor /= 2
            baseTime -= levelFactor
        }
        if (baseTime <= 1) {
            return rand(1, 3)
        }
        return rand(2, baseTime)
    }
}