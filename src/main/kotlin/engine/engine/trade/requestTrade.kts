package engine.trade

import api.bot.action.trading.BotTradeManager.tradeRequests
import api.predef.*
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
            targetPlr.combat.inCombat() || targetPlr.overlays.hasWindow() -> plr.sendMessage(Messages.busy(targetPlr))
            plr.overlays.hasWindow() -> plr.sendMessage(Messages.busy())
            else -> {
                plr.sendMessage("Sending trade offer...")
                (targetPlr as? Bot)?.tradeRequests?.addRequest(plr)
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