import api.*
import io.luna.game.event.impl.WidgetItemClickEvent.WidgetItemFirstClickEvent
import io.luna.game.model.item.shop.ShopInterface

/**
 * Runs [action] if a [ShopInterface] is currently open.
 */
fun forInter(msg: WidgetItemFirstClickEvent, action: (ShopInterface) -> Unit) {
    val inter = msg.plr.interfaces.get(ShopInterface::class)
    if (inter != null) {
        action(inter)
    }
}

/**
 * Send the item's shop value.
 */
on(WidgetItemFirstClickEvent::class)
    .args(3900)
    .run { msg ->
        forInter(msg) { it.shop.sendBuyValue(msg.plr, msg.index) }
    }

/**
 * Send the item's sell value.
 */
on(WidgetItemFirstClickEvent::class)
    .args(3823)
    .run { msg ->
        forInter(msg) { it.shop.sendSellValue(msg.plr, msg.index) }
    }
