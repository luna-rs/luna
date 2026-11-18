package api.bot.action

import io.luna.game.model.World
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.Shop
import io.luna.game.model.mob.bot.Bot
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/**
 * Quotes finite native shop supplies without buying, reserving stock or assuming future restocks.
 * Callers run on the game thread and revalidate travel, stock and prices through ordinary acquisition.
 * The shared item index is refreshed once per world tick so sold-in general-store goods can be discovered.
 *
 * @author lare96
 */
object BotSupplySources {
    const val MAX_QUANTITY = 100
    private const val MAX_ITEM_TYPES = 256

    /**
     * Current supplies and a unit ceiling that also covers smaller purchases as this finite stock is depleted.
     * Total coins is the native cost of the complete quoted quantity before any stock or price changes.
     *
     * @author lare96
     */
    data class Quote(val quantity: Int, val maximumUnitPrice: Long, val totalCoins: Long)

    /**
     * One world's item-to-shop index. Quantities and prices are deliberately read from live shops.
     *
     * @author lare96
     */
    private data class Index(val tick: Long, val shopCount: Int, val shops: Map<Int, List<WeakReference<Shop>>>)

    /**
     * One source's bounded native quote, before combining supplies from several shops.
     *
     * @author lare96
     */
    private data class Source(val shop: Shop, val quote: Quote)

    private val indexes = WeakHashMap<World, Index>()

    /**
     * Quotes currently stocked GP supplies in the bot's world.
     */
    fun quote(bot: Bot, id: Int, maxQuantity: Int = MAX_QUANTITY): Quote? =
        quote(bot.world, id, maxQuantity)

    /**
     * Quotes currently stocked GP supplies. The purchase still requires a visit to a reachable seller.
     */
    fun quote(world: World, id: Int, maxQuantity: Int = MAX_QUANTITY): Quote? =
        quoteStock(world, id, maxQuantity)

    /**
     * Quotes a bounded basket in the bot's world.
     */
    fun quotes(bot: Bot, ids: Collection<Int>, maxQuantity: Int = MAX_QUANTITY): Map<Int, Quote> =
        quotes(bot.world, ids, maxQuantity)

    /**
     * Quotes a bounded basket of currently stocked GP supplies.
     */
    fun quotes(world: World, ids: Collection<Int>, maxQuantity: Int = MAX_QUANTITY): Map<Int, Quote> =
        ids.take(MAX_ITEM_TYPES).distinct().mapNotNull { id -> quote(world, id, maxQuantity)?.let { id to it } }.toMap()

    private fun index(world: World): Index {
        val shops = world.shops.values()
        val tick = world.currentTick
        val previous = indexes[world]
        if (previous != null && previous.tick == tick && previous.shopCount == shops.size) return previous
        val byId = HashMap<Int, MutableList<WeakReference<Shop>>>()
        for (shop in shops) {
            if (shop.currency != Currency.COINS || shop.npcSellers.isEmpty()) continue
            val reference = WeakReference(shop)
            for (id in shop.itemIds) byId.getOrPut(id) { ArrayList() }.add(reference)
        }
        return Index(tick, shops.size, byId).also { indexes[world] = it }
    }

    private fun quoteStock(world: World, id: Int, maxQuantity: Int): Quote? {
        if (id < 0 || maxQuantity < 1) return null
        val limit = maxQuantity.coerceAtMost(MAX_QUANTITY)
        val sources = index(world).shops[id].orEmpty().mapNotNull { reference ->
            val shop = reference.get() ?: return@mapNotNull null
            nativeQuote(shop, id, limit)?.let { Source(shop, it) }
        }.sortedWith(compareBy<Source> { it.quote.maximumUnitPrice }.thenBy { it.shop.name })
        var quantity = 0
        var totalCoins = 0L
        var maximumUnitPrice = 0L
        for ((shop, available) in sources) {
            val amount = minOf(limit - quantity, available.quantity)
            val current = if (amount == available.quantity) available else nativeQuote(shop, id, amount) ?: continue
            quantity += current.quantity
            totalCoins += current.totalCoins
            maximumUnitPrice = maxOf(maximumUnitPrice, current.maximumUnitPrice)
            if (quantity == limit) break
        }
        return if (quantity > 0) Quote(quantity, maximumUnitPrice, totalCoins) else null
    }

    private fun nativeQuote(shop: Shop, id: Int, maxQuantity: Int): Quote? {
        val slot = shop.items.computeIndexForId(id)
        if (slot < 0) return null
        val item = shop.items[slot] ?: return null
        if (item.id != id || item.isDynamic || item.amount < 1) return null
        val quantity = minOf(maxQuantity, item.amount)
        var previous = 0L
        var maximumUnitPrice = 0L
        for (amount in 1..quantity) {
            val total = shop.computeBuyValue(id, amount).orElse(-1).toLong()
            if (total < 1L || total < previous) return null
            // Two independently rounded native batch totals can differ from their exact marginal by one coin.
            val marginal = total - previous + if (amount > 1) 1L else 0L
            maximumUnitPrice = maxOf(maximumUnitPrice, marginal)
            previous = total
        }
        return Quote(quantity, maximumUnitPrice, previous)
    }
}
