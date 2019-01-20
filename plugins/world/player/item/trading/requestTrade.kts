import api.predef.*
import io.luna.game.action.DistancedAction
import io.luna.game.event.entity.player.PlayerClickEvent.PlayerFourthClickEvent
import io.luna.game.model.mob.Player
import world.player.item.trading.OfferTradeInterface
import world.player.item.trading.OfferTradeInterface.Companion.tradingWith

/**
 * A [DistancedAction] that sends a trade request when close enough to a Player.
 */
class RequestAction(val plr: Player,
                    val other: Player) : DistancedAction<Player>(plr, other.position, 1, true) {

    override fun execute() {
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
            other.sendMessage(plr.username + ":tradereq:")
        }
        plr.tradingWith = other.index
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
        else -> plr.submitAction(RequestAction(plr, other))
    }
}

/**
 *  Send request if the [Player] has the trade interaction option.
 */
on(PlayerFourthClickEvent::class)
    .filter { plr.interactions.contains(INTERACTION_TRADE) }
    .then { request(this) }