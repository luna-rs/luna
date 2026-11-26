package game.bot.scripts.skills

import api.bot.action.trading.BotTradeIntentions
import api.predef.ext.totalItemAmount
import game.skill.firemaking.ColouredLog
import game.skill.firemaking.Log
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot

/**
 * Gives social bots a small supply goal for their preferred fire colour when they own ordinary logs.
 *
 * @author lare96
 */
internal object FirelighterStock {
    private const val TARGET = 6

    /**
     * Spreads demand across colours and leaves pricing, budget and fulfilment to the normal buying visit.
     */
    fun request(bot: Bot) {
        if (!bot.personality.isSocial || bot.totalItemAmount(Log.NORMAL.id) == 0) return
        val colours = ColouredLog.entries
        val preferred = colours[Math.floorMod(bot.username.orEmpty().hashCode(), colours.size)]
        BotTradeIntentions.recordSupplies(bot, listOf(Item(preferred.firelighter)), stockTarget = TARGET)
    }

    /**
     * Lets a social bot choose ordinary logs for a visit when it already has firelighters to use.
     */
    fun canMakeColouredFires(bot: Bot): Boolean =
        bot.personality.isSocial && bot.totalItemAmount(Log.NORMAL.id) > 0 &&
            ColouredLog.entries.any { bot.totalItemAmount(it.firelighter) > 0 }
}
