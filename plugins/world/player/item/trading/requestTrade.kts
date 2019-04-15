import api.predef.*
import io.luna.game.action.Action
import io.luna.game.action.DistancedAction
import io.luna.game.event.impl.PlayerClickEvent.PlayerFourthClickEvent
import io.luna.game.model.mob.Player
import world.player.item.trading.OfferTradeInterface

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

/**
 * Request to trade with another [Player].
 */
fun request(msg: PlayerFourthClickEvent) {
    val plr = msg.plr
    val other = msg.other

    when {
        plr.interfaces.isStandardOpen -> plr.sendMessage("You are busy.")
        other.interfaces.isStandardOpen -> plr.sendMessage("That player is busy.")
        else -> plr.submitAction(TradeRequestAction(plr, other))
    }
}

/**
 *  Send request if the [Player] has the trade interaction option.
 */
on(PlayerFourthClickEvent::class)
    .filter { plr.interactions.contains(INTERACTION_TRADE) }
    .then { request(this) }