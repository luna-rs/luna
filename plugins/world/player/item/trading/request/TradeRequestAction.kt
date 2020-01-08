package world.player.item.trading.request

import api.predef.*
import io.luna.game.action.Action
import io.luna.game.action.DistancedAction
import io.luna.game.model.mob.Player
import world.player.item.trading.tradeScreen.OfferTradeInterface

/**
 * A [DistancedAction] that sends a trade request when close enough to a Player.
 */
class TradeRequestAction(val plr: Player,
                         val to: Player) : DistancedAction<Player>(plr, to.position, 1) {

    override fun ignoreIf(other: Action<*>?): Boolean =
        when (other) {
            is TradeRequestAction -> to == other.to
            else -> false
        }


    override fun withinDistance() {
        if (plr.index == to.tradingWith) {
            // They've both requested each other, open offer screen.
            plr.walking.clear()
            to.walking.clear()

            plr.interfaces.open(OfferTradeInterface(to))
            to.interfaces.open(OfferTradeInterface(plr))

            plr.interact(to)
            to.interact(plr)

            plr.tradingWith = -1
            to.tradingWith = -1
        } else {
            // Send trade request, wait for response.
            plr.sendMessage("Sending trade request...")
            plr.interact(to)
            to.sendMessage(plr.username + ":tradereq:")
        }
        plr.tradingWith = to.index
    }
}