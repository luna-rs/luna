package api.bot.action.trading

import api.bot.Suspendable.waitFor
import api.bot.action.trading.BotTradeManager.REQUEST_WAIT_SECONDS
import api.predef.ext.*
import engine.trade.OfferTradeInterface
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.bot.Bot
import kotlin.time.Duration.Companion.seconds

/**
 * Represents the stage where a bot sends a trade request to another player and waits for the trade interface to open.
 *
 * If the trade request is successfully sent, this stage waits up to [REQUEST_WAIT_SECONDS] for the
 * [OfferTradeInterface] to appear in the bot's overlays. Once opened, the trade process advances to a
 * [BotTradeOfferStage].
 *
 * @param bot The bot initiating the trade.
 * @param other The player receiving the trade request.
 * @param purpose The purpose of the trade.
 * @author lare96
 */
class BotTradeRequestStage(
    bot: Bot,
    other: Player,
    purpose: TradePurpose
) : BotTradeStage(bot, other, purpose, null) {

    /**
     * Sends a trade request to the other player and waits for the trade offer interface to open.
     *
     * @return The next [BotTradeOfferStage] if the request was sent and the trade interface opened within the
     * timeout, otherwise `null`.
     */
    suspend fun sendAndAwait(): BotTradeOfferStage? {
        return if (bot.actionHandler.interactions.interact(3, other)) {
            if (waitFor(REQUEST_WAIT_SECONDS.seconds) { OfferTradeInterface::class in bot.overlays })
                BotTradeOfferStage(bot, other, purpose, this)
            else null
        } else {
            null
        }
    }
}