package api.bot.action

import api.predef.ext.totalItemAmount
import game.bot.scripts.trading.TradeBotScript
import game.player.item.consume.potion.PotionFamily
import io.luna.game.model.mob.bot.Bot

/**
 * Starts one exact, expiring native trade for executable personal potion stock.
 *
 * @author lare96
 */
internal object TripPotionSupply {
    fun quotes(bot: Bot, family: PotionFamily): List<BotPotionTrip.Quote> =
        TradeBotScript.potionSupply(bot, family).map {
            BotPotionTrip.Quote(it.id, it.quantity, it.unitPrice, false)
        }

    suspend fun buy(bot: Bot, quote: BotPotionTrip.Quote, deadline: Long, allowed: () -> Boolean) {
        val owner = bot.scriptStack.current() ?: return
        if (!allowed()) return
        val family = PotionFamily.forId(quote.id) ?: return
        val others = family.ids.filterNot { it == quote.id }
        val otherStock = others.sumOf { bot.totalItemAmount(it).toLong() * family.doses(it) }
        val trade = TradeBotScript.buySpecific(bot, quote.id,
            Math.addExact(bot.totalItemAmount(quote.id), quote.quantity), deadline,
            quotedUnitPrice = quote.unitPrice, tripPurchase = {
                !owner.isTerminated() && !owner.isFinished() &&
                    others.sumOf { bot.totalItemAmount(it).toLong() * family.doses(it) } <= otherStock
            }) ?: return
        if (allowed()) bot.scriptStack.pushHead(trade)
    }
}
