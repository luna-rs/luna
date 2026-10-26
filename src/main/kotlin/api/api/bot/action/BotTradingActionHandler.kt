package api.bot.action

import io.luna.game.model.mob.bot.Bot
import api.predef.ext.*
import engine.trade.ConfirmTradeInterface
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.PlayerContextMenuOption.TRADE

/**
 * Handles bot actions related to player-to-player trading.
 *
 * Provides trade-request, final-confirmation, and window-closing primitives through normal client outputs.
 * Trade stages handle offer editing, participant ownership, and observing interface transitions. A submitted request
 * does not by itself establish that a trade screen opened or that either participant accepted.
 *
 * @param bot The bot performing trade actions.
 * @param handler The parent action handler.
 * @author lare96
 */
class BotTradingActionHandler(private val bot: Bot, private val handler: BotActionHandler) {

    /**
     * Approaches another player and submits the native trade interaction while this bot may trade.
     *
     * Matches the server's request filter by checking this bot's [TRADE] menu option. The recipient's menu and either
     * player's Follow option do not determine this permission. The interaction handler rechecks it throughout approach
     * and immediately before sending the click, so a menu change cannot authorize a stale trade interaction.
     * Native controller and trade-request validation still decide whether the request opens a trade screen.
     *
     * @param plr The player to trade with.
     * @return `true` if the interaction handler confirms reach after submitting the click; `false` if permission is
     * absent or lost, or interaction fails. A successful result does not confirm that a trade interface opened.
     */
    suspend fun sendTradeRequest(plr: Player): Boolean {
        return handler.interactions.interact(TRADE.index, plr) {
            TRADE in bot.contextMenu
        }
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
