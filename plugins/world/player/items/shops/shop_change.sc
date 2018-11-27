import io.luna.game.event.impl.WidgetItemClickEvent
import io.luna.game.event.impl.WidgetItemClickEvent.{WidgetItemSecondClickEvent, WidgetItemThirdClickEvent}
import io.luna.game.model.item.shop.ShopInterface


/* Buy and sell constant values. */
private val BUY = true
private val SELL = false


/* Either buys or sells an item. */
private def shop(msg: WidgetItemClickEvent, amount: Int, buy: Boolean) = {
  val plr = msg.plr
  plr.interfaces.
    get(classOf[ShopInterface]).
    foreach { inter =>
      if (buy)
        inter.getShop.buy(plr, msg.index, amount)
      else
        inter.getShop.sell(plr, msg.index, amount)
    }
}


/* Buy/sell 1. */
on[WidgetItemSecondClickEvent].
  args { 3900 }.
  run { shop(_, 1, BUY) }

on[WidgetItemSecondClickEvent].
  args { 3823 }.
  run { shop(_, 1, SELL) }


/* Buy/sell 5. */
on[WidgetItemThirdClickEvent].
  args { 3900 }.
  run { shop(_, 5, BUY) }

on[WidgetItemThirdClickEvent].
  args { 3823 }.
  run { shop(_, 5, SELL) }


/* Buy/sell 10. */
on[WidgetItemThirdClickEvent].
  args { 3900 }.
  run { shop(_, 10, BUY) }

on[WidgetItemThirdClickEvent].
  args { 3823 }.
  run { shop(_, 10, SELL) }


