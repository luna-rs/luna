import api.predef.*
import io.luna.game.event.impl.WidgetItemClickEvent.WidgetItemFirstClickEvent
import io.luna.game.model.item.shop.Shop
import io.luna.game.model.item.shop.ShopInterface

/**
 * Runs [action] if a [ShopInterface] is currently open.
 */
fun getShop(msg: WidgetItemFirstClickEvent, action: Shop.() -> Unit) {
    val inter = msg.plr.getInterface(ShopInterface::class)
    if (inter != null) {
        action(inter.shop)
    }
}

/**
 * Send the item's shop value.
 */
on(WidgetItemFirstClickEvent::class)
    .condition { it.widgetId == 3900 }
    .then {
        getShop(it) { sendBuyValue(it.plr, it.index) }
    }

/**
 * Send the item's sell value.
 */
on(WidgetItemFirstClickEvent::class)
    .condition { it.widgetId == 3823 }
    .then {
        getShop(it) { sendSellValue(it.plr, it.index) }
    }
