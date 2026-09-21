package api.bot.action.trading

import io.luna.game.model.mob.Player
import io.luna.game.model.mob.bot.Bot

/**
 * Represents a stage in a bot-driven trade interaction.
 *
 * Trade stages model the current point in the trading flow between a [bot] and another [Player].
 * Each stage may optionally reference the [lastStage] that led to it, allowing trade logic to inspect
 * or recover previous context when advancing, declining, or reverting the trade.
 *
 * The base stage provides shared behavior common to all trade stages. Specific stages, such as the
 * request stage, confirmation stage, or offer stage, should extend this class and implement their own
 * stage-specific actions.
 *
 * @param bot The bot participating in the trade.
 * @param other The player trading with the bot.
 * @param lastStage The previous trade stage, or `null` if this is the first stage in the flow.
 * @author lare96
 */
open class BotTradeStage(val bot: Bot,
                         val other: Player,
                         val purpose: TradePurpose,
                         val lastStage: BotTradeStage?) {

    /**
     * A flag determining if this trade has been declined.
     */
    var declined = false
        private set

    /**
     * Declines the current trade stage.
     *
     * During the trade request stage, declining does nothing because no trade interface has been opened
     * yet. For all later stages, this closes the bot's active overlay windows, which exits the current trade
     * interface.
     */
    fun decline() {
        // Does nothing during the request stage.
        if (this !is BotTradeRequestStage) {
            declined = true
            bot.overlays.closeWindows()
            bot.log("Trade declined.")
        }
    }

}