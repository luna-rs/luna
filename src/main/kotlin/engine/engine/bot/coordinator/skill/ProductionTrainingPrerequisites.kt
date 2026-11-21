package engine.bot.coordinator.skill

import api.bot.script.ProductionRecipe
import api.bot.zone.ProductionZones
import api.predef.SKILL_CRAFTING
import api.predef.SKILL_FLETCHING
import api.predef.SKILL_SMITHING
import game.skill.crafting.textileCrafting.Textile
import game.skill.fletching.attachArrow.Arrow
import game.skill.fletching.darts.Dart
import game.skill.fletching.ogre.BrutalArrow
import game.skill.smithing.smithBar.SmithingTable
import io.luna.game.model.mob.bot.Bot

/**
 * Native cross-skill inputs that can finish a Fletching training goal.
 *
 * @author lare96
 */
internal object ProductionTrainingPrerequisites {
    private val arrowTips = Arrow.entries.filter { it != Arrow.HEADLESS_ARROW }.mapTo(hashSetOf()) { it.tip }
    private val dartTips = Dart.entries.mapTo(hashSetOf()) { it.tip }
    private val nails by lazy {
        SmithingTable.NAILS.items.map { it.item.id }.intersect(BrutalArrow.entries.map { it.nails }.toSet())
    }

    fun input(consumer: ProductionRecipe): Int? {
        if (consumer.skill != SKILL_FLETCHING) return null
        val inputs = consumer.materials.keys
        return when {
            consumer.key.startsWith("string-bow:") || consumer.key == "ogre:STRING_BOW" ->
                Textile.BOWSTRING.processedItem.id.takeIf { it in inputs }
            consumer.key.startsWith("arrow:") -> inputs.firstOrNull { it in arrowTips }
            consumer.key.startsWith("ammunition:DART:") -> inputs.firstOrNull { it in dartTips }
            consumer.key.startsWith("ogre:BRUTAL:") -> inputs.firstOrNull { it in nails }
            else -> null
        }
    }

    fun permits(consumer: ProductionRecipe, item: Int, producer: ProductionRecipe): Boolean {
        if (input(consumer) != item) return false
        return if (item == Textile.BOWSTRING.processedItem.id) {
            producer.skill == SKILL_CRAFTING && producer.key == "textile:BOWSTRING"
        } else producer.skill == SKILL_SMITHING && producer.key == "smith:$item"
    }

    /**
     * Estimates the additional trip to a usable wheel, anvil or furnace using the native work zones.
     */
    fun stationTicks(bot: Bot, recipe: ProductionRecipe): Double? {
        val zones = when {
            recipe.key == "textile:BOWSTRING" -> ProductionZones.SPINNING
            recipe.key.startsWith("smith:") -> ProductionZones.ANVILS
            recipe.key.startsWith("smelt:") -> ProductionZones.FURNACES
            else -> return 0.0
        }
        val nearest = zones.filter { it.entryBlocker(bot) == null }
            .minOfOrNull { it.inside.computeLongestDistance(bot.position) } ?: return null
        return visitTicks(nearest.toLong())
    }

    fun visitTicks(distance: Long): Double = 25.0 + distance * 0.6
}
