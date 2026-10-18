package game.skill.woodcutting

import api.predef.*
import game.skill.Skills
import game.skill.woodcutting.cutTree.Axe
import game.skill.woodcutting.cutTree.Tree
import io.luna.Luna

/**
 * Contains utility functions related to the woodcutting skill.
 *
 * @author lare96
 */
object Woodcutting {

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
        val trees = Tree.ALL.values
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
     * Determines if a tree falls after giving a log. Normal trees give one log. Other trees have a 1 in 8 chance each
     * time they give a log, or every tree does if `use_317_tree_stumps` is set.
     */
    fun fallsAfterLog(tree: Tree): Boolean =
        tree == Tree.NORMAL || rand().nextInt(8) == 0

    /**
     * A function that determines if a log was successfully cut from a tree.
     */
    fun success(level: Int, tree: Tree, axe: Axe): Boolean = successRate(level, tree, axe) > rand().nextDouble()

    /**
     * The chance of cutting a log from [tree] with [axe] at [level]. Boosts count, but only up to level 99.
     */
    fun successRate(level: Int, tree: Tree, axe: Axe): Double {
        val (low, high) = axe.chances[tree]!!
        return Skills.successRate(low, high, minOf(level, 99))
    }
}