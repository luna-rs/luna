package api.bot.action.trading

import io.luna.game.model.def.WantedItemDefinition
import io.luna.game.model.mob.bot.Bot

/**
 * Keeps merchant decisions on native ownership and untaxed player trades in the public server.
 *
 * @author lare96
 */
internal object BotMerchantMarket {
    fun personalTargets(bot: Bot): List<WantedItemDefinition> = BotTradeIntentions.purchases(bot)
    fun available(bot: Bot): Boolean = false
    fun busy(bot: Bot): Boolean = false
    fun positions(bot: Bot): List<BotMerchantCapital.Position> = emptyList()
    fun fundedNet(bot: Bot, id: Int, quantity: Int): Long = 0L
}
