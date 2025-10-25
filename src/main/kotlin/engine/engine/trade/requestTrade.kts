package engine.engine.trade

import api.predef.*
import io.luna.game.event.impl.PlayerClickEvent.PlayerFourthClickEvent
import io.luna.game.model.mob.Player
import game.player.Messages

/**
 * Request to trade with another [Player].
 */
fun sendTradeRequest(msg: PlayerFourthClickEvent) {
    val plr = msg.plr
    val other = msg.targetPlr

    when {
        plr.interfaces.isStandardOpen -> plr.sendMessage(Messages.BUSY)
        other.interfaces.isStandardOpen -> plr.sendMessage(Messages.INTERACT_BUSY)
        else -> {
            if (plr.index == other.tradingWith) {
                // They've both requested each other, open offer screen.
                plr.walking.clear()
                other.walking.clear()

                plr.interfaces.open(OfferTradeInterface(other))
                other.interfaces.open(OfferTradeInterface(plr))

                plr.interact(other)
                other.interact(plr)

                plr.tradingWith = -1
                other.tradingWith = -1
            } else {
                // Send trade request, wait for response.
                plr.sendMessage("Sending trade request...")
                plr.interact(other)
                other.sendMessage("${plr.username}:tradereq:")
                plr.tradingWith = other.index
            }
        }
    }
}

on(PlayerFourthClickEvent::class)
    .filter { plr.interactions.contains(INTERACTION_TRADE) }
    .then { sendTradeRequest(this) }