package game.bot.scripts.trading

import api.bot.action.trading.BotMerchantProceeds
import api.bot.action.trading.BotShopResale
import io.luna.game.model.mob.bot.Bot

/**
 * Schedules a bounded sale from paid shop stock while leaving reserves with its owner.
 *
 * @author lare96
 */
internal object ShopPurchaseOffers {
    internal fun queue(bot: Bot, purchases: List<BotShopResale.Batch>): Boolean {
        if (purchases.isEmpty() || !bot.preferences.isMerchant) return false
        val deadline = System.currentTimeMillis() + 300_000L
        val sale = BotShopResale.combinedBatches(purchases).asSequence()
            .filter { BotShopResale.canResell(it.id) }
            .sortedByDescending { BotShopResale.guide(bot.world, it.id).toLong() * it.quantity }
            .firstNotNullOfOrNull { batch ->
                val amount = BotShopResale.saleQuantity(bot, batch)
                val price = BotShopResale.floor(batch.cost, batch.quantity, taxed = false,
                    margin = BotMerchantProceeds.margin(bot))
                if (amount < 1 || price !in 1..Int.MAX_VALUE.toLong() ||
                    price > BotShopResale.guide(bot.world, batch.id) && BotShopResale.demand(bot, batch.id)(price) < 1)
                    return@firstNotNullOfOrNull null
                NativeProductionSaleScript.createShopListing(bot, batch.id, amount, price, deadline)
            } ?: return false
        bot.scriptStack.softPushHead(sale)
        return true
    }
}
