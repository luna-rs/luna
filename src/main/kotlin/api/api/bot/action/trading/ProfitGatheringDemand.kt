package api.bot.action.trading

import api.bot.script.ProfitGatheringGoal
import io.luna.game.model.mob.bot.Bot
import io.luna.util.RandomUtils
import kotlin.math.ln1p
import kotlin.math.pow

/**
 * Prices existing native gathering against bounded funded consumer quotes and actual competing stock.
 * These are conservative activity-boundary hints; native transactions still validate their own funding.
 *
 * @author lare96
 */
internal object ProfitGatheringDemand {
    /**
     * One output's remaining demand after owned, listed and prospective supply.
     *
     * @author lare96
     */
    data class Quote(val itemId: Int, val quantity: Int, val minimumUnitPrice: Long, val score: Double,
                     val marketOnly: Boolean = false)

    /**
     * Keeps personality variation among economically useful alternatives rather than level ranking.
     */
    fun <T> select(bot: Bot, options: Map<T, Quote>): T? = RandomUtils.weightedRoll(options.mapValues {
        ln1p(it.value.score).pow(0.5 + bot.personality.intelligence.coerceIn(0.0, 1.0) +
            bot.personality.dexterity.coerceIn(0.0, 1.0) * 0.25)
    })

    internal fun capacity(demand: Long, supply: Long, owned: Long, promised: Long): Int =
        (demand.coerceAtLeast(0L) - supply.coerceAtLeast(0L) - owned.coerceAtLeast(0L) -
            promised.coerceAtLeast(0L)).coerceIn(0L, 56L).toInt()

    fun inputPrice(bot: Bot, id: Int): Double = bot.world.economy.getPrice(id).toDouble().coerceAtLeast(0.0)

    /**
     * Reads at most thirty-two leased advertisers, without scanning the player population.
     */
    fun quote(bot: Bot, id: Int, ticks: Double = 1.0, unitCost: Double = 0.0,
              minimumUnitPrice: Long = 1L, excluding: String? = null, marketOnly: Boolean = false,
              gathered: Int = 0): Quote? {
        if (!ticks.isFinite() || ticks <= 0.0 || !unitCost.isFinite() || unitCost < 0.0) return null
        val now = System.currentTimeMillis()
        val stock = owned(bot, id)
        if (marketOnly) {
            return NativeOutputDemand.depth(bot, id, now, excluding).mapNotNull { depth ->
                val quantity = capacity(depth.quantity.toLong(), 0, stock, 0)
                val proceeds = depth.unitPrice - unitCost
                val saleQuantity = quantity + gathered
                if (quantity < 1 || depth.unitPrice < minimumUnitPrice || !proceeds.isFinite() || proceeds <= 0.0 ||
                    !BotTradeManager.hasNativeSaleStock(bot, id, saleQuantity) ||
                    depth.unitPrice < (BotTradeManager.MINIMUM_OFFER_VALUE + saleQuantity - 1L) / saleQuantity)
                    return@mapNotNull null
                val floor = maxOf(minimumUnitPrice, unitCost.toLong() + 1L)
                val score = proceeds / ticks * quantity / (quantity + stock).coerceAtLeast(1L)
                Quote(id, quantity, floor, score, marketOnly = true)
            }.maxByOrNull { it.score }
        }
        val buyers = HashMap<String, Pair<Long, Int>>()
        val suppliers = ArrayList<Pair<Long, Int>>()
        for (trader in NativeOutputDemand.traders(bot)) {
            trader.profitGatheringPurchase(id)?.let { bid ->
                buyers[trader.bot.username.lowercase()] = bid.price to bid.item.amount
            }
            trader.profitGatheringSupply(id)?.let { suppliers += it.price to it.item.amount }
        }

        val eligible = buyers.values.filter { it.first >= minimumUnitPrice && it.second > 0 }
        if (eligible.isEmpty()) return null
        val price = eligible.minOf { it.first }
        val demand = eligible.sumOf { it.second.toLong() }
        val supply = suppliers.filter { it.first <= price }.sumOf { it.second.toLong() }
        val advertised = suppliers.sumOf { it.second.toLong() }
        val promised = NativeOutputDemand.promised(bot.world, id, now, excluding)
        val quantity = capacity(demand, supply, stock, promised)
        if (price * quantity < BotTradeManager.MINIMUM_OFFER_VALUE) return null
        val proceeds = price * 1.0 - unitCost
        if (quantity < 1 || !proceeds.isFinite() || proceeds <= 0.0) return null
        return Quote(id, quantity, minimumUnitPrice.coerceAtLeast((unitCost / 1.0).toLong() + 1L),
            proceeds / ticks * quantity.toDouble() / (quantity + advertised + stock + promised).coerceAtLeast(1L))
    }

    /**
     * Includes notes in the owner's existing physical supply.
     */
    fun owned(bot: Bot, id: Int): Long = ProfitGatheringGoal.physical(bot, id)

    fun reserve(bot: Bot, goal: ProfitGatheringGoal, quantity: Int): Boolean {
        if (quantity < 1) return false
        val output = mapOf(goal.itemId to quantity + owned(bot, goal.itemId))
        return if (goal.marketOnly)
            NativeOutputDemand.reserve(bot, goal.identity, output, goal.deadline,
                mapOf(goal.itemId to goal.minimumUnitPrice))
        else NativeOutputDemand.track(bot, goal.identity, output, goal.deadline)
    }

    fun release(identity: String) = NativeOutputDemand.release(identity)
}
