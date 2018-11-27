import io.luna.game.event.impl.WidgetItemClickEvent.WidgetItemFirstClickEvent
import io.luna.game.model.item.shop.ShopInterface


/* Send the item's shop value. */
on[WidgetItemFirstClickEvent].
  args { 3900 }.
  run { msg =>
    msg.plr.interfaces.
      get(classOf[ShopInterface]).
      foreach { _.getShop.sendBuyValue(msg.plr, msg.index) }
  }

/* Send the item's sell value. */
on[WidgetItemFirstClickEvent].
  args { 3823 }.
  run { msg =>
    msg.plr.interfaces.
      get(classOf[ShopInterface]).
      foreach { _.getShop.sendSellValue(msg.plr, msg.index) }
  }
