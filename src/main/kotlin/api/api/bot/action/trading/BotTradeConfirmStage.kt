package api.bot.action.trading

import api.bot.Suspendable.naturalDecisionDelay
import api.bot.Suspendable.waitFor
import api.bot.action.trading.BotTradeManager.CONFIRM_WAIT_SECONDS
import api.predef.*
import api.predef.ext.contains
import engine.trade.ConfirmTradeInterface
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.bot.Bot
import kotlin.time.Duration.Companion.seconds

class BotTradeConfirmStage(bot: Bot,
                           other: Player,
                           purpose: TradePurpose,
                           lastStage: BotTradeStage?) : BotTradeStage(bot, other, purpose, lastStage) {

    var scammed = false
        private set
    var confirmed = false
        private set

    suspend fun confirmAndAwait(): Boolean {
        if(ConfirmTradeInterface::class in bot.overlays) {
            scammed = isTradingScammer()
            bot.log("Analyzing trade contents and intentions [${other.username}] scamming=$scammed")
            if(scammed) {
                bot.naturalDecisionDelay()
                // Bot notices scam before trade is completed.
                if (!bot.personality.isDumb || rand(64) != 0) {
                    bot.log("Declining trade to prevent scam.")
                    decline()
                    bot.preferences.adjustFeelingsToward(other.username, -0.50)
                    // TODO announce someone tried to scam, <x> is scammer, whatever
                    return true
                }
            }
            bot.output.clickButton(3546)
            confirmed = true
            waitFor(CONFIRM_WAIT_SECONDS.seconds) { !bot.overlays.hasWindow() }

            // Bot only notices scam after the trade is completed.
            if (scammed) {
                bot.log("Noticed scam by ${other.username} post-trade.")
                // todo notices AFTER trade is done that they were possibly scammed
                // todo announce it, with different context (post trade context)
                bot.preferences.adjustFeelingsToward(other.username, -0.75)
            }
            return true
        }
        bot.log("Confirm trade interface is not open.")
        return false
    }

    private fun isTradingScammer(): Boolean {
        //return scrutiny.searchFunction.invoke(bot, container)
        // todo scrutinize OTHER container not this one...
        return false
    }

    private fun resolveScrutiny(): TradeScrutiny {
        // todo merchants paranoid
        // todo intelligent bots smart, intelligent + social paranoid
        // todo dumb bots don't check
        // todo otherwise what you get depends on 50% social/50% intelligence
        // todo feelings toward in preferences can boost/lower scrutiny as well

        return TradeScrutiny.SMART
    }
}