import io.luna.game.event.impl.WidgetItemClickEvent
import io.luna.game.event.impl.WidgetItemClickEvent._
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.inter.{AmountInputInterface, OfferTradeInterface}


/* Add and remove constant values. */
private val ADD = true
private val REMOVE = false


/* Either adds or removes an item from the trade screen. */
private def trade(msg: WidgetItemClickEvent, amount: Int, add: Boolean) = {
  val plr = msg.plr

  def doTrade(inter: OfferTradeInterface, amt: Int) = if (add)
    inter.add(plr, msg.index, amt) else inter.remove(plr, msg.index, amt)

  plr.interfaces.
    get(classOf[OfferTradeInterface]).
    foreach { offer =>
      if (amount == -1) {
        doTrade(offer, plr.inventory.computeAmountForId(msg.itemId))
      } else {
        doTrade(offer, amount)
      }
    }
}


/* Offer 1. */
on[WidgetItemFirstClickEvent].
  args { 3322 }.
  run { trade(_, 1, ADD) }

on[WidgetItemFirstClickEvent].
  args { 3415 }.
  run { trade(_, 1, REMOVE) }


/* Offer 5. */
on[WidgetItemSecondClickEvent].
  args { 3322 }.
  run { trade(_, 5, ADD) }

on[WidgetItemSecondClickEvent].
  args { 3415 }.
  run { trade(_, 5, REMOVE) }


/* Offer 10. */
on[WidgetItemThirdClickEvent].
  args { 3322 }.
  run { trade(_, 10, ADD) }

on[WidgetItemThirdClickEvent].
  args { 3415 }.
  run { trade(_, 10, REMOVE) }


/* Offer all. */
on[WidgetItemFourthClickEvent].
  args { 3322 }.
  run { trade(_, -1, ADD) }

on[WidgetItemFourthClickEvent].
  args { 3415 }.
  run { trade(_, -1, REMOVE) }


/* Offer (x).  */
on[WidgetItemFifthClickEvent].
  args { 3322 }.
  run { msg =>
    msg.plr.interfaces.open(new AmountInputInterface {
      override def onAmountInput(player: Player, value: Int): Unit =
        trade(msg, value, ADD)
    })
  }

on[WidgetItemFifthClickEvent].
  args { 3415 }.
  run { msg =>
    msg.plr.interfaces.open(new AmountInputInterface {
      override def onAmountInput(player: Player, value: Int): Unit =
        trade(msg, value, REMOVE)
    })
  }