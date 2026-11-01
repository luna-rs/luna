package api.bot.action.trading

import api.predef.world
import com.google.common.math.LongMath
import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Favors established market items without excluding stock that has no trading history yet.
 *
 * @author lare96
 */
object BotTradeSelection {

    /**
     * The maximum number of different stacks carried for a merchant sale.
     */
    internal const val MERCHANT_STACK_LIMIT = 7

    /**
     * Merchant sale stacks must exceed this asking value in coins.
     */
    private const val MINIMUM_MERCHANT_STACK_VALUE = 1_000L

    /**
     * Selects the seven most valuable eligible stacks, then sells the most frequently traded ones first.
     * Full stack values use the quoted unit prices and saturated long arithmetic. Recorded samples break
     * value ties during selection; stack value and item id break sample ties in the resulting sale order.
     * History is snapshotted once per quote, and input quotes are retained for the current banking trip.
     */
    internal fun merchantSales(items: List<BotTradeItem>,
                               samples: (Int) -> Long = { world.economy.prices[it]?.samples ?: 0L }): List<BotTradeItem> {
        val eligible = items.filter { it.price > 0 && it.item.amount > 0 }.associateWith {
            LongMath.saturatedMultiply(it.item.amount.toLong(), it.price)
        }.filterValues { it > MINIMUM_MERCHANT_STACK_VALUE }
        val history = eligible.keys.associateWith { samples(it.item.id).coerceAtLeast(0L) }
        val byValue = compareByDescending<BotTradeItem> { eligible.getValue(it) }
            .thenByDescending { history.getValue(it) }.thenBy { it.item.id }
        val bySamples = compareByDescending<BotTradeItem> { history.getValue(it) }.then(byValue)
        return eligible.keys.sortedWith(byValue).take(MERCHANT_STACK_LIMIT).sortedWith(bySamples)
    }

    /**
     * Reads observed history and the fading initial popularity seed without creating price records.
     */
    fun popularity(id: Int): Double = world.economy.getPopularity(id)

    /**
     * Produces a weighted order without replacement. Square-root scaling softens the advantage of heavily
     * traded items; the baseline weight gives new items a chance and makes an empty economy uniform.
     * Sample counts are converted before adding the baseline so even Long.MAX_VALUE remains finite.
     */
    internal fun <T> order(items: List<T>, random: Random = Random.Default,
                           samples: (T) -> Number): List<T> =
        items.map { it to (-ln(1.0 - random.nextDouble()) /
            sqrt(1.0 + samples(it).toDouble().coerceAtLeast(0.0))) }
            .sortedBy { it.second }
            .map { it.first }

    /**
     * Orders complete quotes without changing their quantities or independently sampled unit prices.
     */
    fun order(items: List<BotTradeItem>): List<BotTradeItem> = order(items) { popularity(it.item.id) }
}
