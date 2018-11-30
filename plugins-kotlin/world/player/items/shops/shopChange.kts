import ShopChange.Change.BUY
import ShopChange.Change.SELL
import api.*
import io.luna.game.event.impl.WidgetItemClickEvent
import io.luna.game.event.impl.WidgetItemClickEvent.WidgetItemSecondClickEvent
import io.luna.game.event.impl.WidgetItemClickEvent.WidgetItemThirdClickEvent
import io.luna.game.model.item.shop.ShopInterface

/**
 * An enum representing the different modifications made to shops.
 */
enum class Change {
    BUY, SELL
}

/**
 * Either buys or sells an item.
 */
fun modify(msg: WidgetItemClickEvent, amount: Int, change: Change) {
    val plr = msg.plr
    val inter = plr.interfaces.get(ShopInterface::class)
    if (inter != null) {
        when (change) {
            BUY -> inter.shop.buy(plr, msg.index, amount)
            SELL -> inter.shop.sell(plr, msg.index, amount)
        }
    }
}

/**
 * Buy/sell 1.
 */
on(WidgetItemSecondClickEvent::class)
    .args(3900)
    .run { modify(it, 1, BUY) }

on(WidgetItemSecondClickEvent::class)
    .args(3823)
    .run { modify(it, 1, SELL) }

/**
 * Buy/sell 5.
 */
on(WidgetItemThirdClickEvent::class)
    .args(3900)
    .run { modify(it, 5, BUY) }

on(WidgetItemThirdClickEvent::class)
    .args(3823)
    .run { modify(it, 5, SELL) }

/**
 * Buy/sell 10.
 */
on(WidgetItemThirdClickEvent::class)
    .args(3900)
    .run { modify(it, 10, BUY) }

on(WidgetItemThirdClickEvent::class)
    .args(3823)
    .run { modify(it, 10, SELL) }


