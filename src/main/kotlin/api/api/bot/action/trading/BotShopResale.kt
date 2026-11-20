package api.bot.action.trading

import api.bot.script.ProductionSaleGuard
import api.predef.ext.totalItemAmount
import io.luna.game.model.World
import io.luna.game.model.def.ItemDefinition
import io.luna.game.model.def.WantedItemDefinition
import io.luna.game.model.def.WantedItemDefinition.WantedItemTag
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.mob.bot.Bot
import java.util.WeakHashMap
import kotlin.math.ceil

/**
 * Sizes cheap shop purchases from both the guide margin and the current quantity of profitable buyers.
 * Useful default goods replenish over several visits as merchandise, while each visit keeps its own purse.
 *
 * @author lare96
 */
internal object BotShopResale {
    private val catalogues = WeakHashMap<World, Pair<Long, List<Int>>>()

    fun guide(world: World, id: Int): Int = BotMerchantValuation.peekGuide(world, id)
        .coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()

    private fun supply(id: Int): Boolean = BotTradeSelection.supplyPriority(id) > 0

    private fun equipment(wanted: WantedItemDefinition): Boolean = wanted.tags().any {
        it == WantedItemTag.WEAPON || it == WantedItemTag.COMBAT_EQUIPMENT || it == WantedItemTag.TOOL
    }

    fun target(id: Int): Int {
        val wanted = WantedItemDefinition.DEFAULT.get(id).orElse(null) ?: return 0
        return when {
            supply(id) -> wanted.target().coerceIn(128, 1_000)
            equipment(wanted) -> wanted.target().coerceIn(4, 12)
            else -> 0
        }
    }

    fun remainingStock(bot: Bot, id: Int): Int {
        val target = if (bot.preferences.isMerchant) target(id) else 0
        val owned = bot.totalItemAmount(id).toLong() + BotMerchantMarket.positions(bot)
            .filter { it.id == id }.sumOf { it.stock + it.bidQuantity }
        val maximum = if (target > 0) target else if (supply(id)) 128 else 26
        return (maximum - owned).coerceAtLeast(0L).toInt()
    }

    fun quietBatch(id: Int, merchant: Boolean): Int = when {
        supply(id) -> if (merchant) 24 else 6
        merchant && target(id) > 0 -> 2
        else -> if (merchant) 5 else 1
    }

    /**
     * Returns the cumulative profitable quantity at a proposed minimum sell price.
     */
    fun demand(bot: Bot, id: Int): (Long) -> Int {
        val depth = NativeOutputDemand.depth(bot, id)
        return { floor -> depth.lastOrNull { it.unitPrice.toLong() >= floor }?.quantity ?: 0 }
    }

    /**
     * Adds at most one small guide-priced quantity to currently profitable demand.
     */
    fun permitsQuantity(total: Int, funded: Int, quiet: Int, guide: Long, floor: Long): Boolean =
        total <= funded || guide >= floor && total - funded <= quiet

    fun floor(cost: Int, quantity: Int, taxed: Boolean, margin: Double): Long {
        if (cost < 1 || quantity < 1) return Long.MAX_VALUE
        val extra = ceil(cost * margin).toLong().coerceAtLeast(1)
        return ProductionSaleGuard.minimumGross(cost + extra - 1L, quantity.toLong(), taxed)
    }

    /**
     * Keeps part of authored stock available to ordinary shop customers.
     * Goods sold into an empty catalogue slot are available at the current native price.
     */
    fun available(authored: Int, current: Int): Int =
        (current - authored / 2 - authored % 2).coerceAtLeast(0)

    fun canResell(id: Int): Boolean = id != Currency.COINS.id && id != 0 &&
        ItemDefinition.ALL.get(id).orElse(null)?.let { it.isTradeable && !it.isNoted } == true

    fun catalogue(world: World): List<Int> {
        val tick = world.currentTick
        catalogues[world]?.let { (refresh, ids) -> if (tick < refresh) return ids }
        val ids = world.shops.values().asSequence()
            .filter { it.currency == Currency.COINS && it.npcSellers.isNotEmpty() }
            .flatMap { it.itemIds.asSequence() }
            .mapNotNull { id -> ItemDefinition.ALL.get(id).orElse(null)?.takeIf { it.isTradeable } }
            .map { it.unnotedId.orElse(it.id()) }
            .filter(::canResell)
            .distinct()
            .toList()
        catalogues[world] = tick + 75L to ids
        return ids
    }
}
