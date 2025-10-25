package game.skill

import api.predef.*
import kotlin.math.floor

/**
 * A utility class containing functions related to skills.
 *
 * @author lare96
 */
object Skills {

    /* https://github.com/luna-rs/luna/issues/422#issuecomment-2980912667 */

    /**
     * Calculates if an action is successful based on the [chance] rates.
     * [level] is the effective current level in the skill to check. This takes into account any
     * bonuses.
     * [maxLevel] is the maximum level in the skill. This is 99 for all skills in OSRS/317.
     */
    fun success(chance: Pair<Int, Int>,
                level: Int,
                maxLevel: Int = 99,
                modifier: (Double) -> Double = { it }): Boolean {
        val (low, high) = chance
        return success(low, high, level, maxLevel, modifier)
    }

    /**
     * Calculates if an action is successful based on the [low] and [high] rates.
     * [level] is the effective current level in the skill to check. This takes into account any
     * bonuses.
     * [maxLevel] is the maximum level in the skill. This is 99 for all skills in OSRS/317.
     */
    fun success(low: Int, high: Int, level: Int, maxLevel: Int = 99, modifier: (Double) -> Double = { it }): Boolean {
        val rate = successRate(low, high, level, maxLevel, modifier)
        return rate > rand().nextDouble()
    }

    /**
     * Calculates the success rate for an action from the [low] and [high] rates.
     * [level] is the effective current level in the skill to check. This takes into account any
     * bonuses.
     * [maxLevel] is the maximum level in the skill. This is 99 for all skills in OSRS/317.
     */
    fun successRate(low: Int,
                    high: Int,
                    level: Int,
                    maxLevel: Int = 99,
                    modifier: (Double) -> Double = { it }): Double {
        val lowRate = floor((low * (maxLevel - level)) / (maxLevel - 1.0))
        val highRate = floor((high * (level - 1)) / (maxLevel - 1.0))
        return modifier((1.0 + floor(lowRate + highRate + 0.5)) / 256.0)
    }

    /**
     * Simulates [success] for [chance] 1000 times at levels 1, 25, 50, 75, and 99. Returns a string builder containing
     * the results. Use this to test success rates for skills.
     */
    fun simulate(name: String, chance: Pair<Int, Int>): StringBuilder {
        val sb = StringBuilder()
        sb.append("Starting simulation for ").append(name).append(' ').append('[').append(chance).append(']')
            .append('\n')
        val testLevels = listOf(1, 25, 50, 75, 99)
        for (level in testLevels) {
            var success = 0
            repeat(1000) {
                if (success(chance, level)) {
                    success++
                }
            }
            sb.append("Level ").append(level).append(' ').append('[')
                .append(successRate(chance.first, chance.second, level)).append(',').append(' ').append(success)
                .append("/1000]").append('\n')
        }
        return sb
    }
}