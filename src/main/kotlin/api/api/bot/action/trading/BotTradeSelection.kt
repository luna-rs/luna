package api.bot.action.trading

import api.predef.world
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
