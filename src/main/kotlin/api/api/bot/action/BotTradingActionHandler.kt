package api.bot.action

import io.luna.game.model.mob.bot.Bot
import api.predef.ext.*
import engine.trade.ConfirmTradeInterface
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.PlayerContextMenuOption

/**
 * Handles bot actions related to player-to-player trading.
 *
 * This handler covers sending trade requests, accepting or declining trade screens, and offering inventory items into
 * the active trade container. It intentionally performs actions through normal client outputs so bot trades behave like
 * regular player trades.
 *
 * @param bot The bot performing trade actions.
 * @param handler The parent action handler.
 * @author lare96
 */
class BotTradingActionHandler(private val bot: Bot, private val handler: BotActionHandler) {

    /**
     * Sends a trade request to another player.
     *
     * The current implementation only attempts the interaction when the target player has the expected context menu
     * entry available.
     *
     * @param plr The player to trade with.
     * @return `true` if the trade interaction was submitted.
     */
    suspend fun sendTradeRequest(plr: Player): Boolean {
        if (PlayerContextMenuOption.FOLLOW in plr.contextMenu) {
            return handler.interactions.interact(4, plr)
        }
        return false
    }

    /**
     * Clicks the accept button on the final trade confirmation screen.
     *
     * @return `true` if the confirmation trade interface was open and the click was sent.
     */
    fun clickFinalAccept(): Boolean {
        if (ConfirmTradeInterface::class in bot.overlays) {
            bot.output.clickButton(3546)
            return true
        }
        return false
    }

    /**
     * Declines the current trade by closing open trade windows.
     */
    fun clickDecline() {
        bot.overlays.closeWindows()
    }

}