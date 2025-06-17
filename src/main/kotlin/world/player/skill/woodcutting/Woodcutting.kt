package world.player.skill.woodcutting

import api.attr.Attr
import api.predef.*
import io.luna.game.model.`object`.GameObject
import world.player.skill.woodcutting.cutTree.Axe
import world.player.skill.woodcutting.cutTree.Tree
import kotlin.math.min

/**
 * Contains utility functions related to the woodcutting skill.
 */
object Woodcutting {

    /**
     * An attribute representing how many logs are left in a tree.
     */
    var GameObject.treeHealth: Int by Attr.int(-1)

    /**
     * Increasing this value will make cutting logs faster while decreasing will do the opposite. Normal values range
     * from -1 to 1.
     */
    private val BONUS_STRENGTH = 0.0

    /**
     * Simulate the result of woodcutting for [attempts] tries, and print the result to the console.
     */
    fun simulate(attempts: Int = 10_000) {
        fun simulateOnce(playerLevel: Int, tree: Tree, axe: Axe) {
            var logsChopped = 0
            var totalTicksElapsed = 0

            repeat(attempts) {
                if (attemptSuccess(playerLevel, tree, axe)) {
                    logsChopped++
                }
                totalTicksElapsed += axe.speed
            }

            val avgTicksPerLog = if (logsChopped > 0) totalTicksElapsed.toDouble() / logsChopped else 0.0
            val avgSecondsPerLog = avgTicksPerLog * 0.6 // Each tick is 600ms
            val logsPerHour = if (avgSecondsPerLog > 0) 3600 / avgSecondsPerLog else 0.0

            println("Axe: ${axe.name}")
            println("Level: $playerLevel vs Tree Level: ${tree.level}")
            println("Logs chopped: $logsChopped")
            println("Average time per log: ${"%.2f".format(avgSecondsPerLog)}s")
            println("Approx logs/hour: ${"%.0f".format(logsPerHour)}")
            println()
        }

        val axes = Axe.VALUES.values
        val trees = Tree.VALUES.values
        val levels = listOf(1, 15, 30, 45, 60, 75, 99)

        for (level in levels) {
            for (tree in trees) {
                if (level < tree.level) {
                    continue
                }
                for (axe in axes) {
                    if (level < axe.level) {
                        continue
                    }
                    simulateOnce(level, tree, axe)
                }
            }
        }
    }

    /**
     * A function that determines if a log was successfully cut from a tree.
     */
    fun attemptSuccess(level: Int, tree: Tree, axe: Axe): Boolean {
        // Normalize level factor, 0.0 (just meets req) to ~1.0 (99 WC vs low-level tree)
        val levelFactor = (level - tree.level).coerceAtLeast(0) / (99.0 - tree.level)

        // Axe modifier: scales chance without hard thresholds
        val axeFactor = axe.strength + BONUS_STRENGTH

        // Effective success chance: soft-capped to 100%
        val successChance = (0.3 + levelFactor * 0.6) * axeFactor
        val finalChance = min(1.0, successChance)

        return rand().nextDouble() <= finalChance
    }
}