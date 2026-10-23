package api.bot.action.trading

import api.predef.ext.*
import engine.trade.ConfirmTradeInterface
import engine.trade.OfferTradeInterface
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.bot.Bot

/**
 * Represents a stage in a bot-driven trade interaction.
 *
 * Trade stages model the current point in the trading flow between a [bot] and another [Player]. The base stage
 * provides shared state and behavior common to all trade stages, while subclasses implement stage-specific actions.
 *
 * @param bot The bot participating in the trade.
 * @param other The player trading with the bot.
 * @author lare96
 */
open class BotTradeStage(
    val bot: Bot,
    val other: Player
) {

    /**
     * Declines the current trade stage.
     *
     * During the trade request stage, declining does nothing because no trade interface has been opened yet. For all
     * later stages, this closes the bot's windows only when its trade interface still belongs to this participant.
     * An unrelated shop, bank, or replacement trade is left open.
     */
    fun decline() {
        // Does nothing during the request stage.
        val offer = bot.overlays[OfferTradeInterface::class]
        val confirm = bot.overlays[ConfirmTradeInterface::class]
        if (this !is BotTradeRequestStage && (offer?.other === other || confirm?.offer?.other === other)) {
            bot.overlays.closeWindows()
        }
    }
}