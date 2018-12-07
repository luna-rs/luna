import api.predef.*
import io.luna.game.event.impl.WidgetItemClickEvent.WidgetItemFirstClickEvent
import io.luna.game.model.item.shop.Shop
import io.luna.game.model.item.shop.ShopInterface
import io.luna.game.model.mob.Player

/**
 * Runs [action] if a [ShopInterface] is currently open.
 */
fun getShop(plr: Player, action: (Shop) -> Unit) {
    val inter = plr.interfaces.get(ShopInterface::class)
    if (inter != null) {
        action(inter.shop)
    }
}

/**
 * Send the item's shop value.
 */
on(WidgetItemFirstClickEvent::class)
    .condition { widgetId == 3900 }
    .then {
        getShop(plr) { it.sendBuyValue(plr, index) }
    }

/**
 * Send the item's sell value.
 */
on(WidgetItemFirstClickEvent::class)
    .condition { widgetId == 3823 }
    .then {
        getShop(plr) { it.sendSellValue(plr, index) }
    }
