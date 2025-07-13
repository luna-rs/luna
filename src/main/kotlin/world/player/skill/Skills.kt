package world.player.skill

import api.predef.rand
import kotlin.math.floor

/**
 * A utility class containing functions related to skills.
 */
object Skills {


    /* https://github.com/luna-rs/luna/issues/422#issuecomment-2980912667 */

    /**
     * Calculates if an action is successful based on the [low] and [high] rates.
     * [level] is the effective current level in the skill to check. This takes into account any
     * bonuses.
     * [maxLevel] is the maximum level in the skill. This is 99 for all skills in OSRS/317.
     */
    fun success(low: Int, high: Int, level: Int, maxLevel: Int = 99): Boolean {
        val rate = successRate(low, high, level, maxLevel)
        return rate > rand().nextDouble()
    }

    /**
     * Calculates the success rate for an action from the [low] and [high] rates.
     * [level] is the effective current level in the skill to check. This takes into account any
     * bonuses.
     * [maxLevel] is the maximum level in the skill. This is 99 for all skills in OSRS/317.
     */
    fun successRate(low: Int, high: Int, level: Int, maxLevel: Int = 99): Double {
        val lowRate = floor((low * (maxLevel - level)) / (maxLevel - 1.0))
        val highRate = floor((high * (level - 1)) / (maxLevel - 1.0))
        return (1.0 + floor(lowRate + highRate + 0.5)) / 256.0
    }
}