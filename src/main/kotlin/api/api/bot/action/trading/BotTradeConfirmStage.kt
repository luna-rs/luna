package api.bot.action.trading

import api.bot.Suspendable.waitFor
import api.bot.action.trading.BotTradeManager.CONFIRM_WAIT_SECONDS
import api.predef.ext.*
import engine.trade.ConfirmTradeInterface
import engine.trade.tradingWith
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.bot.Bot
import kotlin.time.Duration.Companion.seconds

/**
 * Handles the final confirmation stage of a bot trade.
 *
 * Sends the confirmation click and waits for the confirmation interface to close. A trade is
 * considered successful only if the original interface reports completion.
 *
 * @param bot The bot performing the trade.
 * @param other The player trading with the bot.
 * @property lastStage The preceding trade offer stage.
 * @author lare96
 */
class BotTradeConfirmStage(
    bot: Bot,
    other: Player,
    val lastStage: BotTradeOfferStage
) : BotTradeStage(bot, other) {

    /**
     * Whether the bot has sent its confirmation click.
     */
    var confirmed = false
        private set

    /**
     * Confirms the trade and waits for the confirmation interface to close.
     *
     * Requires an active [ConfirmTradeInterface] before sending the confirmation click. Waits up
     * to [CONFIRM_WAIT_SECONDS] for the original interface to be removed or replaced, then checks
     * whether the trade completed successfully.
     *
     * @return `true` if the interface closes within the timeout and reports a completed trade.
     */
    suspend fun confirmAndAwait(): Boolean {
        val confirm = bot.overlays[ConfirmTradeInterface::class]
            ?: return false
        if (confirm.offer.other !== other) return false

        bot.output.clickButton(3546)
        confirmed = true

        val closed = waitFor(CONFIRM_WAIT_SECONDS.seconds) {
            bot.overlays[ConfirmTradeInterface::class] !== confirm
        }
        return closed && confirm.completed
    }
}