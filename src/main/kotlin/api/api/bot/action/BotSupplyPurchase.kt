package api.bot.action

import api.attr.Attr
import api.bot.action.trading.BotFoodDemand
import game.bot.scripts.trading.TradeBotScript
import api.bot.script.BotScript.ActionSafety
import api.predef.ext.totalItemAmount
import engine.controllers.Controllers.inWilderness
import io.luna.game.model.EntityState
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import kotlin.time.Duration.Companion.seconds

/**
 * Quotes and buys finite stock through the native shops or an active direct seller.
 *
 * @author lare96
 */
internal class BotSupplyPurchase(private val bot: Bot, private val handler: BotActionHandler) {
    /**
     * An executable lot and its maximum funding requirement.
     *
     * @author lare96
     */
    data class Quote(val item: Item, val unitPrice: Long, val coins: Long, val direct: Boolean)

    fun shopQuote(id: Int, amount: Int): Quote? =
        BotSupplySources.quote(bot, id, amount)?.takeIf { it.quantity >= amount }?.let {
            // Reserve a coin per unit for rounding if a native visit splits the quoted purchase.
            Quote(Item(id, amount), it.maximumUnitPrice, it.totalCoins + amount, false)
        }

    companion object {
        private val directRetryAt = Attr.map<Int, Long>()
    }

    fun directQuote(id: Int, amount: Int, offers: List<BotFoodDemand.Basket> =
        TradeBotScript.carriedSupply(bot, setOf(id))): Quote? {
        if ((bot.attributes()[directRetryAt.attr][id] ?: 0L) > System.currentTimeMillis()) return null
        return offers.asSequence().filter { it.quantity >= amount && it.unitPrice in 1..Int.MAX_VALUE.toLong() }
            .minByOrNull { it.unitPrice }?.let { Quote(Item(id, amount), it.unitPrice, it.unitPrice * amount, true) }
    }

    suspend fun buy(quote: Quote, allowed: () -> Boolean) {
        val item = quote.item
        if (!allowed() || !safe() || quote.coins !in 1L..minOf(coins(), Int.MAX_VALUE.toLong())) return
        val target = (bot.totalItemAmount(item.id).toLong() + item.amount).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        if (quote.direct) {
            val owner = bot.scriptStack.current() ?: return
            val visit = 90.seconds
            val deadline = System.currentTimeMillis() + visit.inWholeMilliseconds
            val trade = TradeBotScript.buySpecific(bot, item.id, target,
                deadline, quotedUnitPrice = quote.unitPrice,
                tripPurchase = { !owner.isTerminated() && !owner.isFinished() && safe() }) ?: return
            if (allowed() && handler.shop.obtainWantedItem(trade, visit))
                bot.attributes()[directRetryAt.attr][item.id] = deadline + 30_000L
        } else {
            handler.shop.obtainWantedItems(mapOf(item.id to target), quote.coins.toInt(),
                mapOf(item.id to quote.unitPrice))
        }
    }

    fun coins(): Long = bot.inventory.computeAmountForId(995).toLong() + bot.bank.computeAmountForId(995)

    fun safe(): Boolean = bot.state == EntityState.ACTIVE && bot.isAlive && !bot.client.isPendingLogout &&
        !bot.inWilderness() && ActionSafety.NON_COMBAT.allows(bot)
}
