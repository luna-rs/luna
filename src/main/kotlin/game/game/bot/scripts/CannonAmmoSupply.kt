package game.bot.scripts

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.bot.action.trading.BotTradeIntentions
import api.bot.action.trading.BotTradePurpose
import api.bot.script.BotScript.ActionSafety
import api.predef.ext.totalItemAmount
import engine.controllers.Controllers.inWilderness
import game.bot.scripts.trading.TradeBotScript
import game.content.dwarfMulticannon.DwarfMulticannon.CANNONBALL
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration.Companion.minutes

/**
 * Publishes affordable cannonball demand and tries one native trade with a prepared seller.
 *
 * @author lare96
 */
internal object CannonAmmoSupply {
    private const val COINS = 995
    private var Bot.nextCannonRestockAt by Attr.long()

    fun canTry(bot: Bot, target: Int): Boolean {
        if (!safe(bot) || System.currentTimeMillis() < bot.nextCannonRestockAt) return false
        val guide = bot.world.economy.getPrice(CANNONBALL)
        return guide > 0 && fundedTarget(bot, target, guide) > bot.totalItemAmount(CANNONBALL)
    }

    /**
     * Pauses the caller for a bounded supply trade when an executable seller is available.
     */
    suspend fun restock(bot: Bot, target: Int) {
        val owner = bot.scriptStack.current() ?: return
        if (!canTry(bot, target)) return
        val now = System.currentTimeMillis()
        bot.nextCannonRestockAt = now + 10.minutes.inWholeMilliseconds
        val owned = bot.totalItemAmount(CANNONBALL)
        val guide = bot.world.economy.getPrice(CANNONBALL)
        val planned = fundedTarget(bot, target, guide)
        BotTradeIntentions.recordSupplies(bot, listOf(Item(CANNONBALL, planned)), stockTarget = planned, now = now)
        val quote = TradeBotScript.preparedSaleQuotes(bot, setOf(CANNONBALL))[CANNONBALL]
            ?.takeIf { it.price <= guide + maxOf(1L, guide / 10L) } ?: return
        val affordable = fundedTarget(bot, target, quote.price)
        val total = minOf(affordable.toLong(), owned.toLong() + quote.item.amount).toInt()
        if (total <= owned) return
        val trade = TradeBotScript.buySpecific(bot, CANNONBALL, total, now + 2.minutes.inWholeMilliseconds,
            quotedUnitPrice = quote.price, tripPurchase = {
                !owner.isTerminated() && !owner.isFinished()
            }) ?: return
        bot.scriptStack.pushHead(trade)
        // Do not mark the caller's run or initial banking complete after pushHead has paused it.
        coroutineContext.ensureActive()
    }

    private fun fundedTarget(bot: Bot, target: Int, price: Long): Int {
        val purse = (bot.totalItemAmount(COINS) * BotTradeIntentions.budgetFraction(bot, BotTradePurpose.BUY)).toLong()
        return minOf(target.toLong(), bot.totalItemAmount(CANNONBALL) + purse / price).toInt()
    }

    private fun safe(bot: Bot) = !bot.inWilderness() && ActionSafety.NON_COMBAT.allows(bot)
}
