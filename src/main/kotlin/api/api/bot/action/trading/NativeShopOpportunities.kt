package api.bot.action.trading

import api.bot.script.ProfitGatheringGoal
import io.luna.game.model.item.shop.Shop
import io.luna.game.model.mob.bot.Bot
import java.util.UUID

/**
 * Ranks a few reachable NPC stock sources against funded native consumption.
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
     * An expiring shop visit and the minimum acceptable unit price for the funded outbound trade.
     *
     * @author lare96
     */
    data class Deal(val shop: Shop, val id: Int, val quantity: Int, val cost: Int, val floor: Long,
                    val score: Double, val deadline: Long, val identity: String = "")

    private data class Hint(val shop: Shop, val id: Int, val depth: List<NativeOutputDemand.Depth>, val score: Double)

    fun preview(bot: Bot, ids: List<Int>, now: Long, capital: BotMerchantCapital.Balance? = null): Deal? {
        if (ids.isEmpty()) return null
        val balance = capital ?: BotMerchantCapital.snapshot(bot)
        val budget = budget(balance)
        if (budget < 1) return null
        val start = Math.floorMod(bot.usernameHash xor bot.world.currentTick, ids.size.toLong()).toInt()
        val hints = ArrayList<Hint>()
        for (step in 0 until minOf(ids.size, MAX_ITEM_QUOTES)) {
            val id = ids[(start + step) % ids.size]
            val shops = bot.world.shops.catalogueSources(id)
            if (shops.isEmpty() || protected(bot, balance, id) || BotTradeManager.saleableAmount(bot, id) > 0) continue
            val depth = NativeOutputDemand.depth(bot, id, now)
            if (depth.isEmpty()) continue
            for (shop in shops.asSequence().take(MAX_SHOPS_PER_ITEM)) {
                val quantity = minOf(stock(shop, id), MAX_BATCH, depth.last().quantity)
                if (quantity < 1) continue
                val firstUnit = shop.computeBuyValue(id, 1).orElse(0)
                if (firstUnit !in 1..budget || depth.first().unitPrice <= firstUnit) continue
                hints += Hint(shop, id, depth, (depth.first().unitPrice - firstUnit).toDouble() * quantity)
            }
        }
        return hints.sortedByDescending { it.score }.asSequence().take(MAX_ADMISSION_QUOTES).mapNotNull { hint ->
            val distance = bot.actionHandler.shop.shopDistance(hint.shop) ?: return@mapNotNull null
            if (distance > MAX_TRAVEL) return@mapNotNull null
            quote(bot, hint.shop, hint.id, MAX_BATCH, budget, balance, hint.depth, now + VISIT_MILLIS,
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
        return quote(bot, deal.shop, deal.id, deal.quantity, minOf(deal.cost, cash, budget(balance)), balance,
            depth, deal.deadline, 1.0, deal.identity)
    }

    fun reserve(bot: Bot, deal: Deal): Boolean =
        NativeOutputDemand.reserve(bot, deal.identity, mapOf(deal.id to deal.quantity.toLong()),
            deal.deadline, minimumPrices = mapOf(deal.id to deal.floor))

    private fun budget(balance: BotMerchantCapital.Balance): Int =
        if (balance.cashRecovery) 0 else minOf(MAX_SPEND, balance.coins / 5L, balance.electiveCoins).toInt()

    private fun protected(bot: Bot, balance: BotMerchantCapital.Balance, id: Int): Boolean =
        balance.personalDeficit(id) > 0L || BotTradeIntentions.wouldRetainFirstEquipment(bot, id) ||
            BotTradeIntentions.retained(bot, id) > ProfitGatheringGoal.physical(bot, id)

    private fun stock(shop: Shop, id: Int): Int {
        val slot = shop.items.computeIndexForId(id)
        if (slot < 0 || shop.items[slot]?.isDynamic != false) return 0
        return purchasable(shop.originalStock(slot), shop.items.computeAmountForIndex(slot))
    }

    internal fun purchasable(authored: Int, current: Int): Int =
        if (authored <= 0) 0 else (current - authored / 2 - authored % 2).coerceAtLeast(0)

    private fun quote(bot: Bot, shop: Shop, id: Int, limit: Int, budget: Int, balance: BotMerchantCapital.Balance,
                      depth: List<NativeOutputDemand.Depth>, deadline: Long, ticks: Double,
                      identity: String = ""): Deal? {
        val maximum = minOf(stock(shop, id), limit, depth.lastOrNull()?.quantity ?: 0)
        val minimumTrade = BotTradeManager.MINIMUM_OFFER_VALUE.toLong()
        var best: Deal? = null
        for (quantity in maximum downTo 1) {
            if (!BotTradeManager.hasNativeSaleStock(bot, id, quantity)) break
            val proceeds = depth.firstOrNull { it.quantity >= quantity }?.unitPrice ?: continue
            val cost = shop.computeBuyValue(id, quantity).orElse(-1)
            if (cost !in 1..budget || !balance.permits(id, quantity, cost.toLong(), personal = false)) continue
            val gross = proceeds * quantity
            val minimumProfit = maxOf(10L, (cost + 9L) / 10L)
            if (gross < minimumTrade || gross - cost < minimumProfit) continue
            val floor = (maxOf(cost + minimumProfit, minimumTrade) + quantity - 1L) / quantity
            val score = (gross - cost).toDouble() / ticks
            if (score > (best?.score ?: 0.0)) best = Deal(shop, id, quantity, cost, floor, score, deadline, identity)
        }
        return best
    }
}
