package engine.trade

import api.predef.*
import game.player.Messages
import io.luna.game.event.impl.PlayerClickEvent.PlayerFourthClickEvent
import io.luna.game.model.mob.Player

/**
 * Request to trade with another [Player].
 */
on(PlayerFourthClickEvent::class)
    .filter { plr.interactions.contains(INTERACTION_TRADE) }
    .then {
        when {
            plr.overlays.hasWindow() -> plr.sendMessage(Messages.BUSY)
            targetPlr.overlays.hasWindow() -> plr.sendMessage(Messages.INTERACT_BUSY)
            else -> {
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
                    plr.sendMessage("Sending trade request...")
                    plr.interact(targetPlr)
                    targetPlr.sendMessage("${plr.username}:tradereq:")
                    plr.tradingWith = targetPlr.index
                }
            }
        }
    }