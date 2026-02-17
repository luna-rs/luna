package game.skill.woodcutting

import api.attr.Attr
import game.skill.Skills
import game.skill.woodcutting.cutTree.Axe
import game.skill.woodcutting.cutTree.Tree
import io.luna.Luna
import io.luna.game.model.`object`.GameObject

/**
 * Contains utility functions related to the woodcutting skill.
 *
 * @author lare96
 */
object Woodcutting {

    /**
     * An attribute representing how many logs are left in a tree.
     */
    var GameObject.treeHealth: Int by Attr.int { -1 }

    /**
     * Simulate the result of woodcutting for [attempts] tries, and print the result.
     */
    fun simulate(attempts: Int = 10_000) {
        fun simulateOnce(playerLevel: Int, tree: Tree, axe: Axe) {
            var logsChopped = 0
            var totalTicksElapsed = 0

            repeat(attempts) {
                if (success(playerLevel, tree, axe)) {
                    logsChopped++
                }
                totalTicksElapsed += Luna.settings().skills().woodcuttingSpeed()
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
    fun success(level: Int, tree: Tree, axe: Axe): Boolean {
        val (low, high) = axe.chances[tree]!!
        return Skills.success(low, high, level)
    }
}