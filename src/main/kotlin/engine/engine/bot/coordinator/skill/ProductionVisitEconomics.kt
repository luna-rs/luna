package engine.bot.coordinator.skill

import api.bot.action.BotSupplySources
import api.bot.action.trading.BotTradeIntentions
import api.bot.action.trading.BotTradeManager
import api.bot.action.trading.BotTradePurpose
import api.bot.action.trading.NativeOutputDemand
import api.bot.script.ProductionEconomics
import api.bot.script.ProductionMode
import api.bot.script.ProductionRecipe
import api.predef.ext.totalItemAmount
import engine.bot.economy.BotProductionUses
import io.luna.game.model.mob.bot.Bot

/**
 * Quotes production visits whose native state is managed by a mill or a farming patch.
 *
 * @author lare96
 */
internal object ProductionVisitEconomics {
    fun purse(bot: Bot): Long = (BotTradeManager.transferableAmount(bot, 995) *
        BotTradeIntentions.budgetFraction(bot, BotTradePurpose.BUY)).toLong()

    private fun unitPrice(bot: Bot, id: Int): Long {
        return BotSupplySources.quote(bot, id)?.maximumUnitPrice
            ?: bot.world.economy.getPrice(id).coerceAtLeast(1L)
    }

    fun quote(bot: Bot, recipe: ProductionRecipe, mode: ProductionMode, requested: Int,
              stock: (Int) -> Long = { bot.totalItemAmount(it).toLong() }): ProductionEconomics.Quote? {
        val limit = if (mode == ProductionMode.PRODUCTION) ProductionEconomics.MAX_PRODUCTION_OPERATIONS
            else ProductionEconomics.MAX_OPERATIONS
        val prices = (recipe.materials.keys + recipe.tools).associateWith { unitPrice(bot, it) }
        val exits = if (mode == ProductionMode.PRODUCTION) emptyMap() else {
            val operations = requested.coerceIn(1, ProductionEconomics.MAX_OPERATIONS)
            val existing = BotProductionUses.recoverableValues(bot, recipe.valuedProducts, operations)
            recipe.valuedProducts.mapValues { (id, amount) ->
                val bid = NativeOutputDemand.depth(bot, id)
                    .filter { it.quantity.toLong() >= amount.toLong() * operations }
                    .maxOfOrNull { it.unitPrice } ?: 0L
                maxOf(existing[id] ?: 0L, bid)
            }
        }
        return ProductionEconomics.quote(recipe, false, requested, Double.MAX_VALUE,
            prices.keys.associateWith(stock), purse(bot), prices, exits,
            allowLoss = mode == ProductionMode.PRODUCTION, operationLimit = limit)
    }
}
