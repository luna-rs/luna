package game.bot.scripts.skills

import api.bot.action.BotActionHandler
import api.bot.action.BotFarmingActionHandler
import api.bot.action.trading.BotTradeIntentions
import api.bot.action.trading.BotTradeManager
import api.bot.action.trading.BotTradePurpose
import api.bot.action.trading.BotMerchantValuation
import api.bot.zone.SubZone
import api.predef.world
import game.skill.farming.Farming
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.ceil
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Obtains a farming visit's real supplies before the ordinary banking and travel lifecycle starts.
 * Native leprechaun stock is retrieved first. Shopping requests only the remaining ownership deficit, so an
 * interrupted storage exchange never purchases a duplicate tool. One deadline covers exchanges and purchases;
 * any useful partial stock remains owned for the caller to re-evaluate against current native work.
 * GP purchases share one normal reserve and current unit-price guard. Selected training visits may impose
 * stricter immutable limits and record each actual payment before the shop coroutine yields.
 *
 * @author lare96
 */
object FarmingSupplyPreparation {
    /**
     * Secures portable minimum quantities for one run. Callers add quantities consumed by separate plots before
     * invoking this method; repeated entries here describe the same reusable requirement and use their maximum.
     */
    suspend fun prepare(
        bot: Bot,
        handler: BotActionHandler,
        items: List<Item>,
        preferred: SubZone?,
        allowed: () -> Boolean,
        budget: Duration = 120.seconds,
        maximumPurchaseCoins: Long = Long.MAX_VALUE,
        maximumUnitPrices: Map<Int, Long>? = null,
        recordSpending: (Int) -> Unit = {}
    ): Boolean {
        require(maximumPurchaseCoins >= 0L)
        require(maximumUnitPrices?.values?.all { it > 0L } != false)
        if (!allowed() || budget <= Duration.ZERO) return false
        val tools = setOf(Farming.RAKE, Farming.SEED_DIBBER, Farming.SPADE, Farming.GARDENING_TROWEL,
            Farming.SECATEURS, Farming.MAGIC_SECATEURS, Farming.EMPTY_WATERING_CAN) + Farming.WATERING_CANS
        val requirements = items.groupBy { it.id }.map { (id, entries) -> Item(id, entries.maxOf { it.amount }) }
            .sortedBy { it.id !in tools }
        BotTradeIntentions.recordSupplies(bot, requirements, tools.intersect(requirements.map { it.id }.toSet()),
            stockTarget = requirements.maxOfOrNull { it.amount } ?: 1)
        return withTimeoutOrNull(minOf(120.seconds, budget)) {
            val stored = requirements.mapNotNull { item ->
                val portable = BotFarmingActionHandler.portableStock(bot, item.id)
                val available = minOf((item.amount - portable).coerceAtLeast(0),
                    BotFarmingActionHandler.storedStock(bot, item.id).toLong())
                if (available == 0L) null else Item(item.id, (portable + available).toInt())
            }
            if (stored.isNotEmpty()) handler.farming.retrieve(stored, preferred, allowed)
            if (!allowed()) return@withTimeoutOrNull false
            val purchases = requirements.mapNotNull { item ->
                val deficit = (item.amount - BotFarmingActionHandler.stock(bot, item.id)).coerceAtLeast(0)
                if (deficit == 0L) null else item.id to
                    (BotFarmingActionHandler.portableStock(bot, item.id) + deficit).toInt()
            }.toMap()
            if (purchases.isNotEmpty()) {
                val coins = BotTradeManager.transferableAmount(bot, 995)
                val fraction = BotTradeIntentions.budgetFraction(bot, BotTradePurpose.BUY)
                val maximumCoins = minOf(maximumPurchaseCoins,
                    (coins.toDouble() * fraction).toLong().coerceIn(0L, Int.MAX_VALUE.toLong())).toInt()
                val unitPrices = purchases.keys.mapNotNull { id ->
                    if (maximumUnitPrices != null && id !in maximumUnitPrices) return@mapNotNull null
                    val guide = world.economy.prices[id]?.price?.takeIf { it.isFinite() && it > 0.0 }
                        ?: BotMerchantValuation.peekGuide(id).toDouble()
                    val current = ceil(guide.coerceAtLeast(1.0) * 2.0).toLong().coerceIn(1L, Int.MAX_VALUE.toLong())
                    id to minOf(current, maximumUnitPrices?.get(id) ?: Long.MAX_VALUE)
                }.toMap()
                handler.shop.obtainWantedItems(purchases, maximumCoins, unitPrices, recordSpending)
            }
            allowed() && requirements.all { BotFarmingActionHandler.portableStock(bot, it.id) >= it.amount }
        } == true
    }
}
