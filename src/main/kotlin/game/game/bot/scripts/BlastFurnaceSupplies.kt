package game.bot.scripts

import api.bot.action.BotSupplySources
import api.bot.action.trading.BotTradePrice
import api.bot.script.ProductionEconomics
import api.bot.script.ProductionPlan
import api.bot.script.ProductionRecipe
import api.predef.SKILL_SMITHING
import api.predef.smithing
import api.predef.itemDef
import game.content.blastFurnace.BlastFurnace
import game.skill.smithing.BarType
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot

/**
 * Prices one finite furnace inventory before asking normal shops or traders to supply it.
 * Saved ore reduces the outside stock needed, while tools and the native access fee remain funded.
 * Training uses the existing GP-per-XP ceiling without assuming an executable sale for the bars.
 *
 * @author lare96
 */
object BlastFurnaceSupplies {
    private const val MAXIMUM_COINS = 5_000L

    private fun owned(bot: Bot, id: Int): Long {
        val note = itemDef(id).notedId.orElse(-1)
        return bot.inventory.computeAmountForId(id).toLong() + bot.bank.computeAmountForId(id) +
            if (note >= 0) bot.inventory.computeAmountForId(note).toLong() else 0L
    }

    /**
     * Selects the largest complete affordable batch that fits after its tools and entry-fee slot.
     * Each recipe is one bulk operation, with targets expressed as total ordinary ownership.
     */
    fun create(bot: Bot, bar: BarType, tools: List<Item>, deadline: Long): ProductionPlan? {
        if (deadline <= System.currentTimeMillis() || bot.smithing.staticLevel < bar.level ||
            bot.smithing.level < bar.level || BlastFurnace.totalBars(bot) > 0 ||
            BlastFurnace.beltCount(bot) > 0) return null
        val ingredients = BlastFurnace.ingredients(bar)
        if (BarType.entries.any { candidate -> candidate.level <= bot.smithing.level &&
                BlastFurnace.ingredients(candidate).all { BlastFurnace.oreCount(bot, it.id) >= it.amount }
            }) return null
        val toolIds = tools.mapTo(linkedSetOf()) { it.id }
        val fee = if (BlastFurnace.hasAccess(bot)) 0 else BlastFurnace.FEE
        val slots = bot.inventory.capacity() - toolIds.size - if (fee > 0) 1 else 0
        val budget = (owned(bot, 995) - fee).coerceIn(0L, MAXIMUM_COINS)
        if (slots < 1 || owned(bot, 995) < fee) return null
        val ids = ingredients.mapTo(linkedSetOf()) { it.id } + toolIds
        val strategy = BotTradePrice.computePriceStrategy(bot)
        val ceilings = ids.associateWith { id ->
            BotSupplySources.quote(bot, id, slots)?.maximumUnitPrice ?: run {
                val guide = bot.world.economy.getPrice(id)
                if (guide > 0L) strategy.applyBuy(guide).coerceAtLeast(1L) else 0L
            }
        }
        if (ceilings.any { (id, price) -> price < 1L && owned(bot, id) == 0L }) return null
        val prices = ceilings.mapValues { (_, price) -> price.coerceAtLeast(1L) }
        val stock = ids.associateWith { owned(bot, it) }
        val maximum = ingredients.minOf { 255 / it.amount }
        for (batches in maximum downTo 1) {
            val materials = ingredients.mapNotNull { ore ->
                val target = (ore.amount * batches - BlastFurnace.oreCount(bot, ore.id)).coerceAtLeast(0)
                if (target > 0) ore.id to target else null
            }.toMap()
            if (materials.isEmpty() || materials.values.sum() > slots) continue
            if ((materials + toolIds.associateWith { 1 }).any { (id, target) ->
                    owned(bot, id) < target && ceilings.getValue(id) < 1L
                }) continue
            val recipe = ProductionRecipe("blast_furnace:${bar.name.lowercase()}", SKILL_SMITHING,
                bar.level, materials, toolIds, mapOf(bar.id to batches), bar.smeltXp * batches)
            val quote = ProductionEconomics.quote(recipe, true, 1, Double.MAX_VALUE, stock, budget,
                prices, emptyMap()) ?: continue
            return ProductionPlan(recipe, true, 1, quote.maximumCoins, prices, deadline)
        }
        return null
    }

    /**
     * Allows recipe selection to consider real funded acquisition instead of requiring owned ore first.
     */
    fun canAcquire(bot: Bot, bar: BarType, tools: List<Item>, deadline: Long): Boolean =
        create(bot, bar, tools, deadline) != null
}
