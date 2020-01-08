package world.player.item.trading.request

import api.predef.*
import io.luna.game.event.impl.PlayerClickEvent.PlayerFourthClickEvent
import io.luna.game.model.mob.Player

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