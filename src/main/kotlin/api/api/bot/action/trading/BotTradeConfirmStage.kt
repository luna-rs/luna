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
 * Handles the confirmation stage of a bot trade.
 *
 * This stage represents the second trade screen, where the bot reviews and confirms the final trade contents.
 *
 * @param bot The bot performing the trade.
 * @param other The player trading with the bot.
 * @param lastStage The stage that preceded this one.
 * @author lare96
 */
class BotTradeConfirmStage(
    bot: Bot,
    other: Player,
    lastStage: BotTradeStage?
) : BotTradeStage(bot, other) {

    /**
     * Whether the bot has confirmed the trade.
     */
    var confirmed = false
        private set

    /**
     * Confirms the trade and waits for it to finish.
     *
     * The confirmation click is only sent while [ConfirmTradeInterface] is open. After confirming, this method waits
     * up to [CONFIRM_WAIT_SECONDS] for the trade interface to close or for the bot to no longer be trading with
     * another player.
     *
     * If the confirmation interface is not open, the trade is declined immediately.
     *
     * @return `true` if the trade finished before the timeout expired, otherwise `false`.
     */
    suspend fun confirmAndAwait(): Boolean {
        if (ConfirmTradeInterface::class in bot.overlays) {
            bot.output.clickButton(3546)
            confirmed = true

            return waitFor(CONFIRM_WAIT_SECONDS.seconds) {
                !bot.overlays.hasWindow() || bot.tradingWith == -1
            }
        }

        bot.log("Confirm trade interface is not open.")
        decline()
        return false
    }
}