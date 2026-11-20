package api.bot.action.trading

import api.bot.action.stockForms
import io.luna.game.model.item.shop.Shop
import io.luna.game.model.mob.bot.Bot
import java.util.UUID

/**
 * Ranks reachable NPC stock against the guide margin and funded native consumption.
 * The native shop retains stock and prices. Only a chosen supplier commits predicted output.
 *
 * @author lare96
 */
internal object NativeShopOpportunities {
    private const val MAX_ITEM_QUOTES = 6
    private const val MAX_SHOPS_PER_ITEM = 4
    private const val MAX_ADMISSION_QUOTES = 4
    private const val MAX_TRAVEL = 240L
    private const val MAX_BATCH = 26
    private const val MAX_SPEND = 25_000L
    private const val VISIT_MILLIS = 120_000L

    /**
     * An expiring shop visit and the minimum acceptable unit price for an outbound trade.
     *
     * @author lare96
     */
    data class Deal(val shop: Shop, val id: Int, val stockId: Int, val quantity: Int, val cost: Int, val floor: Long,
                    val score: Double, val deadline: Long, val funded: Boolean, val identity: String = "")

    /**
     * One eligible shop stock form and the matching demand for the base commodity.
     *
     * @author lare96
     */
    private data class Hint(val shop: Shop, val id: Int, val stockId: Int,
                            val depth: List<NativeOutputDemand.Depth>, val score: Double)

    fun preview(bot: Bot, ids: List<Int>, now: Long, capital: BotMerchantCapital.Balance? = null): Deal? {
        val balance = capital ?: BotMerchantCapital.snapshot(bot)
        val budget = budget(balance)
        if (budget < 1) return null
        val seed = bot.usernameHash xor bot.world.currentTick
        fun sample(catalogue: List<Int>): List<Int> {
            if (catalogue.isEmpty()) return emptyList()
            val start = Math.floorMod(seed, catalogue.size.toLong()).toInt()
            return List(minOf(catalogue.size, MAX_ITEM_QUOTES)) { catalogue[(start + it) % catalogue.size] }
        }
        val hints = ArrayList<Hint>()
        for (id in (sample(ids) + sample(BotShopResale.catalogue(bot.world))).distinct()) {
            if (!BotShopResale.canResell(id)) continue
            val forms = stockForms(id)
            val shops = forms.asSequence().flatMap { bot.world.shops.catalogueSources(it) }
                .distinct().take(MAX_SHOPS_PER_ITEM).toList()
            if (shops.isEmpty() || protected(bot, balance, id) || BotShopResale.remainingStock(bot, id) < 1) continue
            val depth = NativeOutputDemand.depth(bot, id, now)
            val quiet = BotShopResale.quietBatch(id, bot.preferences.isMerchant)
            val guide = BotShopResale.guide(bot.world, id)
            for (shop in shops) {
                for (stockId in forms) {
                    val quantity = minOf(stock(shop, stockId), MAX_BATCH)
                    if (quantity < 1) continue
                    val firstUnit = shop.computeBuyValue(stockId, 1).orElse(0)
                    if (firstUnit !in 1..budget) continue
                    val forecast = (guide - firstUnit).toLong() * minOf(quantity, quiet)
                    val funded = ((depth.firstOrNull()?.unitPrice ?: 0L) - firstUnit) *
                        minOf(quantity, depth.lastOrNull()?.quantity ?: 0)
                    if (maxOf(forecast, funded) <= 0L) continue
                    hints += Hint(shop, id, stockId, depth, maxOf(forecast, funded).toDouble())
                }
            }
        }
        return hints.sortedByDescending { it.score }.asSequence().take(MAX_ADMISSION_QUOTES).mapNotNull { hint ->
            val distance = bot.actionHandler.shop.shopDistance(hint.shop) ?: return@mapNotNull null
            if (distance > MAX_TRAVEL) return@mapNotNull null
            quote(bot, hint.shop, hint.id, hint.stockId, MAX_BATCH, budget, balance, hint.depth, now + VISIT_MILLIS,
                32.0 + 2.0 * distance)
        }.maxByOrNull { it.score }
    }

    fun admit(bot: Bot, deal: Deal): Deal? =
        deal.copy(identity = UUID.randomUUID().toString()).takeIf { reserve(bot, it) }

    fun revalidate(bot: Bot, deal: Deal, cash: Int = deal.cost,
                   now: Long = System.currentTimeMillis()): Deal? {
        if (now >= deal.deadline || !BotMarketPreference.profitSeeking(bot) ||
            bot.world.shops.lookup(deal.shop.name).orElse(null) !== deal.shop) return null
        val balance = BotMerchantCapital.snapshot(bot)
        if (protected(bot, balance, deal.id)) return null
        val depth = NativeOutputDemand.depth(bot, deal.id, now, excluding = deal.identity)
        return quote(bot, deal.shop, deal.id, deal.stockId, deal.quantity,
            minOf(deal.cost, cash, budget(balance)), balance, depth, deal.deadline, 1.0, deal.identity)
    }

    fun reserve(bot: Bot, deal: Deal): Boolean {
        val quantity = mapOf(deal.id to deal.quantity.toLong())
        return if (deal.funded) NativeOutputDemand.reserve(bot, deal.identity, quantity,
            deal.deadline, minimumPrices = mapOf(deal.id to deal.floor))
        else NativeOutputDemand.track(bot, deal.identity, quantity, deal.deadline)
    }

    private fun budget(balance: BotMerchantCapital.Balance): Int =
        minOf(MAX_SPEND, balance.coins / 5L,
            maxOf(balance.stockingCoins, if (balance.cashRecovery) 0L else balance.electiveCoins)).toInt()

    private fun protected(bot: Bot, balance: BotMerchantCapital.Balance, id: Int): Boolean =
        balance.personalDeficit(id) > 0L || BotTradeIntentions.productionLimit(bot, id) != null

    private fun stock(shop: Shop, id: Int): Int {
        val slot = shop.items.computeIndexForId(id)
        if (slot < 0 || shop.items[slot]?.isDynamic != false) return 0
        return purchasable(shop.originalStock(slot), shop.items.computeAmountForIndex(slot))
    }

    internal fun purchasable(authored: Int, current: Int): Int =
        if (authored <= 0) 0 else (current - authored / 2 - authored % 2).coerceAtLeast(0)

    private fun quote(bot: Bot, shop: Shop, id: Int, stockId: Int, limit: Int, budget: Int, balance: BotMerchantCapital.Balance,
                      depth: List<NativeOutputDemand.Depth>, deadline: Long, ticks: Double,
                      identity: String = ""): Deal? {
        val quiet = BotShopResale.quietBatch(id, bot.preferences.isMerchant)
        val guide = BotShopResale.guide(bot.world, id).toLong()
        val maximum = minOf(stock(shop, stockId), limit, BotShopResale.remainingStock(bot, id),
            ((depth.lastOrNull()?.quantity ?: 0) + quiet.toLong()).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
        val minimumTrade = BotTradeManager.MINIMUM_OFFER_VALUE.toLong()
        val margin = BotMerchantProceeds.margin(bot)
        var best: Deal? = null
        for (quantity in maximum downTo 1) {
            val cost = shop.computeBuyValue(stockId, quantity).orElse(-1)
            if (cost !in 1..budget || !balance.permitsShopPurchase(id, quantity, cost.toLong())) continue
            val floor = BotShopResale.floor(cost, quantity, taxed = false, margin)
            val fundedQuantity = depth.lastOrNull { it.unitPrice >= floor }?.quantity ?: 0
            val live = depth.firstOrNull { it.quantity >= quantity && it.unitPrice >= floor &&
                it.unitPrice * quantity >= minimumTrade }?.unitPrice
            val funded = live != null && BotTradeManager.hasNativeSaleStock(bot, id, quantity)
            if (!funded && (guide < floor ||
                    !BotShopResale.permitsQuantity(quantity, fundedQuantity, quiet, guide, floor))) continue
            val gross = (if (funded) requireNotNull(live) else guide) * quantity
            val score = (gross - cost).toDouble() / ticks
            if (score > (best?.score ?: 0.0)) best =
                Deal(shop, id, stockId, quantity, cost, floor, score, deadline, funded, identity)
        }
        return best
    }
}
