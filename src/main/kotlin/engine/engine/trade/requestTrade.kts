package engine.trade

import api.bot.action.trading.BotTradeManager.tradeRequests
import api.predef.*
import game.bot.scripts.trading.TradeBotScript
import game.player.Messages
import io.luna.game.event.impl.PlayerClickEvent.PlayerFourthClickEvent
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.bot.Bot

/**
 * Request to trade with another [Player].
 */
on(PlayerFourthClickEvent::class)
    .filter { plr.contextMenu.contains(OPTION_TRADE) }
    .then {
        when {
            plr.isEventBot || targetPlr.isEventBot -> plr.sendMessage("Event bots cannot trade.")
            targetPlr.combat.inCombat() || targetPlr.overlays.hasWindow() -> plr.sendMessage(Messages.busy(targetPlr))
            plr.overlays.hasWindow() -> plr.sendMessage(Messages.busy())
            else -> {
                val targetBot = targetPlr as? Bot
                if ((targetBot?.scriptStack?.current() as? TradeBotScript)?.refuseHumanTradeRequest(plr) == true) return@then
                plr.sendMessage("Sending trade offer...")
                targetBot?.tradeRequests?.addRequest(plr)
                if (plr.index == targetPlr.tradingWith) {
                    // They've both requested each other, open offer screen.
                    plr.walking.clear()
                    targetPlr.walking.clear()

                    plr.overlays.open(OfferTradeInterface(targetPlr))
                    targetPlr.overlays.open(OfferTradeInterface(plr))

                    plr.interact(targetPlr)
                    targetPlr.interact(plr)

                    plr.tradingWith = -1
                    targetPlr.tradingWith = -1
                } else {
                    // Send trade request, wait for response.
                    plr.interact(targetPlr)
                    targetPlr.sendMessage("${plr.username}:tradereq:")
                    plr.tradingWith = targetPlr.index
                }
            }
        }
    }
