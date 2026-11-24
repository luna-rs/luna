package engine.bot.coordinator.skill

import api.bot.action.BotSupplySources
import api.bot.action.BotSupplySources.VisitQuote
import api.bot.action.trading.BotTradeManager
import api.bot.action.trading.NativeOutputDemand
import api.bot.script.ProductionRecipe
import io.luna.game.model.mob.bot.Bot

/**
 * Quotes a training batch from owned supplies, live shop stock and executable trades.
 *
 * @author lare96
 */
internal class ProductionTrainingSupplies(private val bot: Bot) {
    private val visits = HashMap<Int, List<VisitQuote>>()

    private fun sources(id: Int): List<VisitQuote> = visits.getOrPut(id) { buildList {
        addAll(BotSupplySources.visits(bot, id, BotSupplySources.MAX_QUANTITY))
        val direct = NativeOutputDemand.traders(bot)
        for (trader in direct) {
            val offer = trader.profitGatheringSupply(id) ?: continue
            val minimum = 1 + (BotTradeManager.MINIMUM_OFFER_VALUE - 1) / offer.price
            if (offer.item.amount < minimum) continue
            add(VisitQuote(offer.item.amount, offer.price,
                bot.position.computeLongestDistance(trader.bot.position).toLong()))
        }
    }.sortedWith(compareBy<VisitQuote> { it.unitPrice }.thenBy { it.distance }) }

    internal fun firstSource(id: Int): VisitQuote? = sources(id).firstOrNull()

    internal fun affordableSources(id: Int, maximumUnitPrice: Long): List<VisitQuote> =
        sources(id).filter { it.unitPrice <= maximumUnitPrice }

    /**
     * Bounds the native work to supplies already owned or currently offered. Reusable tools are bought once.
     */
    fun limit(recipe: ProductionRecipe, stock: Map<Int, Long>, requested: Int): Int {
        fun available(id: Int, wanted: Long): Long {
            val owned = stock.getValue(id)
            return if (owned >= wanted) owned else owned + sources(id).sumOf { it.quantity.toLong() }
        }
        if (recipe.tools.any { available(it, 1) < 1L }) return 0
        return recipe.materials.entries.fold(requested) { limit, (id, amount) ->
            minOf(limit.toLong(), available(id, amount.toLong() * limit) / amount).toInt()
        }
    }

    /**
     * Captures conservative unit ceilings for real deficits and values owned-only inputs at a token coin.
     * A fully owned basket therefore needs no purchase allowance.
     */
    fun prices(recipe: ProductionRecipe, stock: Map<Int, Long>, operations: Int): Map<Int, Long> =
        deficits(recipe, stock, operations).mapValues { (id, deficit) ->
            var ceiling = 1L
            forSources(id, deficit) { ceiling = it.unitPrice }
            ceiling
        }

    /**
     * Compares acquisition trips on top of native processing and banking time.
     */
    fun travelTicks(recipe: ProductionRecipe, stock: Map<Int, Long>, operations: Int): Double =
        deficits(recipe, stock, operations).entries.sumOf { (id, deficit) ->
            var travel = 0.0
            forSources(id, deficit) { travel += ProductionTrainingPrerequisites.visitTicks(it.distance) }
            travel
        }

    private fun deficits(recipe: ProductionRecipe, stock: Map<Int, Long>, operations: Int): Map<Int, Long> =
        (recipe.materials.mapValues { (_, amount) -> amount.toLong() * operations } +
            recipe.tools.associateWith { 1L }).mapValues { (id, amount) ->
            (amount - stock.getValue(id)).coerceAtLeast(0L)
        }

    private inline fun forSources(id: Int, deficit: Long, use: (VisitQuote) -> Unit) {
        if (deficit <= 0L) return
        var remaining = deficit
        for (source in sources(id)) {
            use(source)
            remaining -= source.quantity
            if (remaining <= 0L) break
        }
    }
}
