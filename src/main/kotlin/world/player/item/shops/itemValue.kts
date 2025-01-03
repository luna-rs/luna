package world.player.item.shops

import api.predef.*
import api.predef.ext.*
import io.luna.game.event.impl.WidgetItemClickEvent.WidgetItemFirstClickEvent
import io.luna.game.model.item.shop.Shop
import io.luna.game.model.item.shop.ShopInterface
import io.luna.game.model.mob.Player

/**
 * Returns the currently open shop.
 */
fun currentShop(plr: Player): Shop? {
    val inter = plr.interfaces.get(ShopInterface::class)
    return inter?.shop
}

/**
 * Send the item's shop value.
 */
on(WidgetItemFirstClickEvent::class)
    .filter { widgetId == 3900 }
    .then { currentShop(plr)?.sendBuyValue(plr, index) }

/**
 * Send the item's sell value.
 */
on(WidgetItemFirstClickEvent::class)
    .filter { widgetId == 3823 }
    .then { currentShop(plr)?.sendSellValue(plr, index) }