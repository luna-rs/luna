package api.bot.action.trading

import com.google.common.math.LongMath
import engine.bot.economy.BotProductionUses
import io.luna.game.model.item.Item
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.Shop
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.brain.BotEmotion.EmotionType
import kotlin.math.floor

/**
 * Separates guide-based sale expectations from native recovery and funded market proceeds.
 * Values are decision estimates; actual settlement and seller tax remainders remain with their native owners.
 *
 * @author lare96
 */
object BotMerchantProceeds {
    /**
     * Applies the maximum existing seller tax once to a whole prospective batch, including remainder uncertainty.
     */
    internal fun net(gross: Long, taxed: Boolean): Long {
        val value = gross.coerceAtLeast(0L)
        return if (!taxed) value else value - value / 20L - if (value % 20L == 0L) 0L else 1L
    }

    fun margin(bot: Bot): Double = 0.05 + bot.personality.intelligence.coerceIn(0.0, 1.0) * 0.10 +
        if (bot.emotions.isFeeling(EmotionType.GREEDY)) 0.10 else 0.0

    /**
     * Prices ordinary speculation without any ownership, rune or order-book probing.
     */
    fun forecastCeiling(bot: Bot, id: Int, quantity: Int): Long {
        if (quantity < 1) return 0L
        val guide = BotMerchantValuation.peekGuide(id).coerceAtLeast(1L)
        val proceeds = net(LongMath.saturatedMultiply(guide, quantity.toLong()), BotMerchantMarket.available(bot))
        return minOf(BotMerchantValuation.resaleBid(guide, 0.0, false, 1.0),
            floor(proceeds.toDouble() / (1.0 + margin(bot))).toLong() / quantity)
    }

    /**
     * Uses the existing native batch/rune/build policy and bounded private bids, never an unavailable shop exit.
     */
    fun recoverable(bot: Bot, id: Int, quantity: Int): Long {
        if (quantity < 1) return 0L
        val native = BotProductionUses.recoverableValues(bot, mapOf(id to quantity), operations = 1, includeRecentTrades = false)[id] ?: 0L
        return maxOf(native, BotMerchantMarket.fundedNet(bot, id, quantity) / quantity)
    }

    /**
     * Retains discounted speculation while checking the actual batch's net expected margin after price adjustment.
     */
    fun ceiling(bot: Bot, id: Int, quantity: Int, ordinary: Long, recovery: Long? = null): Long {
        if (!BotMerchantCapital.active(bot)) return ordinary
        if (quantity < 1 || ordinary < 1L) return 0L
        val guide = BotMerchantValuation.peekGuide(id).coerceAtLeast(1L)
        val forecast = net(LongMath.saturatedMultiply(guide, quantity.toLong()), BotMerchantMarket.available(bot))
        val forecastCost = floor(forecast.toDouble() / (1.0 + margin(bot))).toLong() / quantity
        val guideDiscount = BotMerchantValuation.resaleBid(guide, 0.0, false, 1.0)
        if (ordinary <= minOf(forecastCost, guideDiscount)) return ordinary
        val exit = recovery ?: recoverable(bot, id, quantity)
        val reference = maxOf(guide, exit)
        val discount = BotMerchantValuation.resaleBid(reference, 0.0, false, 1.0)
        val proceeds = maxOf(forecast, LongMath.saturatedMultiply(exit, quantity.toLong()))
        val cost = floor(proceeds.toDouble() / (1.0 + margin(bot))).toLong()
        return minOf(ordinary, discount, cost / quantity)
    }

    /**
     * One real full-batch shop quote, with no stock or coins reserved by valuation.
     *
     * @author lare96
     */
    data class Purchase(val quantity: Int, val coins: Int)

    /**
     * Quotes one shop purchase at current prices, with a small guide-backed allowance beyond funded demand.
     */
    fun shopPurchase(bot: Bot, shop: Shop, id: Int, maximum: Int, budget: Int,
                     resaleUnit: Long? = null, capital: BotMerchantCapital.Balance = BotMerchantCapital.snapshot(bot)): Purchase? {
        if (!BotMerchantCapital.active(bot) || shop.currency != Currency.COINS || shop.npcSellers.isEmpty()) return null
        val slot = shop.items.computeIndexForId(id)
        val stock = if (slot >= 0) shop.items[slot] else null
        if (stock == null || stock.isDynamic || !stock.itemDef.isTradeable || maximum < 1 || budget < 1) return null
        val base = stock.unnotedItemDef.id()
        if (!BotShopResale.canResell(base) || BotTradeIntentions.productionLimit(bot, base) != null) return null
        val guide = minOf(BotShopResale.guide(bot.world, base).toLong(), resaleUnit ?: Long.MAX_VALUE)
        val demand = BotShopResale.demand(bot, base)
        val quiet = BotShopResale.quietBatch(base, bot.preferences.isMerchant)
        val available = BotShopResale.available(shop.originalStock(slot), stock.amount)
        val limit = minOf(maximum, available, 100, BotShopResale.remainingStock(bot, base),
            (demand(1) + quiet.toLong()).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
        val taxed = BotMerchantMarket.available(bot)
        val margin = margin(bot)
        for (quantity in limit downTo 1) {
            val cost = shop.computeBuyValue(id, quantity).orElse(-1)
            if (cost !in 1..budget || !bot.inventory.hasSpaceFor(Item(id, quantity)) ||
                !capital.permitsShopPurchase(base, quantity, cost.toLong())) continue
            val floor = BotShopResale.floor(cost, quantity, taxed, margin)
            if (BotShopResale.permitsQuantity(quantity, demand(floor), quiet, guide, floor)) return Purchase(quantity, cost)
        }
        return null
    }
}
