import io.luna.game.action.DistancedAction
import io.luna.game.event.impl.PlayerClickEvent.PlayerFourthClickEvent
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.inter.OfferTradeInterface


/* A DistancedAction that sends a trade request when close enough to a Player. */
private final class RequestAction(plr: Player, other: Player) extends
  DistancedAction[Player](plr, other.position, 1, true) {

  override def execute() {
    if (plr.index == other.getTradingWith) {
      // They've both requested each other, open offer screen.
      plr.stopWalking
      other.stopWalking

      plr.interfaces.open(new OfferTradeInterface(other))
      other.interfaces.open(new OfferTradeInterface(plr))

      plr.interact(other)
      other.interact(plr)

      plr.resetTradingWith()
      other.resetTradingWith()
    } else {
      // Send trade request, wait for response.
      plr.sendMessage("Sending trade request...")
      plr.interact(other)
      other.sendMessage(plr.name + ":tradereq:")
    }
    plr.setTradingWith(other.index)
  }
}


/* Request to trade with another Player. */
private def request(msg: PlayerFourthClickEvent): Unit = {
  val plr = msg.plr
  val other = msg.other
  if (plr.interfaces.isStandardOpen) {
    other.sendMessage("You are busy.")
    return
  }
  if (other.interfaces.isStandardOpen) {
    other.sendMessage("That player is busy.")
    return
  }
  plr.submitAction(new RequestAction(plr, other))
}


/* Send request if the Player has the trade interaction option. */
on[PlayerFourthClickEvent].
  condition { _.plr.interactions.contains(INTERACTION_TRADE) }.
  run { request }