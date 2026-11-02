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
 * @author lare96
 */
class BotTradeRequestStage(
    bot: Bot,
    other: Player
) : BotTradeStage(bot, other) {

    /**
     * Sends a permitted native trade request to the other player and waits for that player's offer interface.
     *
     * An already-open offer with the expected participant proceeds without sending another request. Otherwise the
     * trading helper checks this bot's Trade option during approach and before the click. Reach or click submission
     * alone does not advance the stage; the matching offer interface must appear within the request timeout.
     *
     * @param canContinue Whether to continue this unanswered request. An existing matching trade takes precedence.
     * @return The next [BotTradeOfferStage] if the request was sent and the trade interface opened within the
     * timeout, otherwise `null`.
     */
    suspend fun sendAndAwait(canContinue: () -> Boolean = { true }): BotTradeOfferStage? =
        awaitRequest(canContinue, null)

    /**
     * Runs [beforeRequest] once immediately before submitting an unanswered native request. An already-open matching
     * trade skips that prerequisite and continues normally; its owner can deliver any still-needed message afterward.
     */
    suspend fun sendAndAwait(beforeRequest: () -> Boolean, canContinue: () -> Boolean): BotTradeOfferStage? =
        awaitRequest(canContinue, beforeRequest)

    /**
     * Keeps legacy requests on their existing action path while supporting a one-time submission prerequisite.
     */
    private suspend fun awaitRequest(canContinue: () -> Boolean, beforeRequest: (() -> Boolean)?): BotTradeOfferStage? {
        fun opened() = bot.overlays[OfferTradeInterface::class]?.other === other
        if (opened()) return BotTradeOfferStage(bot, other, this)
        if (!canContinue()) return null
        val sent = if (beforeRequest == null) {
            bot.actionHandler.trading.sendTradeRequest(other) { opened() || canContinue() }
        } else bot.actionHandler.trading.sendTradeRequest(other,
            beforeRequest = { opened() || beforeRequest() }, canContinue = { opened() || canContinue() })
        if (opened()) return BotTradeOfferStage(bot, other, this)
        if (!sent || !canContinue()) return null
        waitFor(REQUEST_WAIT_SECONDS.seconds) {
            opened() || !canContinue()
        }
        return if (opened()) BotTradeOfferStage(bot, other, this) else null
    }
}
