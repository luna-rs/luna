package api.bot.action.trading

import api.predef.world
import com.google.common.math.LongMath
import io.luna.Luna
import io.luna.game.model.def.ItemDefinition
import io.luna.game.model.def.WantedItemDefinition
import io.luna.game.model.def.WantedItemDefinition.WantedItemTag
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
     * Selects a bounded batch of common supplies before occasional equipment and miscellaneous stock.
     * Recorded samples order each category, followed by saturated full-stack value and item id.
     * Quantities and sampled unit quotes are retained for the current banking trip.
     */
    internal fun merchantSales(items: List<BotTradeItem>,
                               random: Random = Random.Default,
                               samples: (Int) -> Long = { world.economy.prices[it]?.samples ?: 0L }): List<BotTradeItem> {
        val settings = Luna.settings().bots()
        val eligible = items.filter { it.price > 0 && it.item.amount > 0 }.associateWith {
            LongMath.saturatedMultiply(it.item.amount.toLong(), it.price)
        }.filterValues { it > settings.merchantSaleMinimumStackValue() }
        val history = eligible.keys.associateWith { samples(it.item.id).coerceAtLeast(0L) }
        val byValue = compareByDescending<BotTradeItem> { eligible.getValue(it) }
            .thenByDescending { history.getValue(it) }.thenBy { it.item.id }
        val bySamples = compareByDescending<BotTradeItem> { history.getValue(it) }.then(byValue)
        return supplyOrder(eligible.keys.sortedWith(bySamples), random) { supplyPriority(it.item.id) }
            .take(settings.merchantSaleStackLimit())
    }

    /**
     * Reads observed history and the fading initial popularity seed without creating price records.
     */
    fun popularity(id: Int): Double = world.economy.getPopularity(id)

    /**
     * Classifies consumable supplies from the existing wanted catalogue. Notes share their base item's category;
     * reusable tools, equipment and unknown items remain occasional purchases rather than bulk supplies.
     */
    fun supplyPriority(id: Int): Int {
        if (id !in 0 until ItemDefinition.SIZE) return 0
        val base = ItemDefinition.ALL.get(id).map { it.unnotedId.orElse(id) }.orElse(id)
        val tags = WantedItemDefinition.DEFAULT.get(base).orElse(null)?.tags() ?: emptySet()
        return when {
            WantedItemTag.SKILLING_CONSUMABLE in tags -> 2
            WantedItemTag.COMBAT_CONSUMABLE in tags -> 1
            else -> 0
        }
    }

    /**
     * Keeps category proportions independent of catalogue size and unit price. While both groups remain,
     * supplies receive 95 percent of choices; skilling receives three quarters of supply choices when both
     * supply categories remain. Each category retains its input order, and exhausted categories fall back
     * to the remaining stock, so useful niche items still trade and no candidate is discarded.
     */
    internal fun <T> supplyOrder(items: List<T>, random: Random = Random.Default,
                                 category: (T) -> Int): List<T> {
        val groups = Array(3) { ArrayList<T>() }
        items.forEach { groups[category(it).coerceIn(0, 2)] += it }
        val offsets = IntArray(3)
        return buildList(items.size) {
            repeat(items.size) {
                val skilling = offsets[2] < groups[2].size
                val combat = offsets[1] < groups[1].size
                val other = offsets[0] < groups[0].size
                val supply = (skilling || combat) && (!other || random.nextDouble() < 0.95)
                val group = if (!supply) 0 else when {
                    !combat -> 2
                    !skilling -> 1
                    random.nextDouble() < 0.75 -> 2
                    else -> 1
                }
                add(groups[group][offsets[group]++])
            }
        }
    }

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
    fun order(items: List<BotTradeItem>): List<BotTradeItem> =
        supplyOrder(order(items) { popularity(it.item.id) }) { supplyPriority(it.item.id) }
}
