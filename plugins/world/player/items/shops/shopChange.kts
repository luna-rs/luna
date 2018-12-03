import api.*
import io.luna.game.event.impl.WidgetItemClickEvent
import io.luna.game.event.impl.WidgetItemClickEvent.*
import io.luna.game.model.item.shop.ShopInterface

/**
 * Represents either a 'buy' or 'sell' modification.
 */
open class Mod

/**
 * Represents a 'buy' modification.
 */
object Buy : Mod()

/**
 * Represents a 'sell' modification.
 */
object Sell : Mod()

/**
 * Either buys or sells an item.
 */
fun modify(msg: WidgetItemClickEvent, amount: Int, mod: Mod) {
    val plr = msg.plr
    val inter = plr.interfaces.get(ShopInterface::class)
    if (inter != null) {
        when (mod) {
            Buy -> inter.shop.buy(plr, msg.index, amount)
            Sell -> inter.shop.sell(plr, msg.index, amount)
        }
    }
}

/**
 * Buy/sell 1.
 */
on(WidgetItemSecondClickEvent::class)
    .args(3900)
    .run { modify(it, 1, Buy) }

on(WidgetItemSecondClickEvent::class)
    .args(3823)
    .run { modify(it, 1, Sell) }

/**
 * Buy/sell 5.
 */
on(WidgetItemThirdClickEvent::class)
    .args(3900)
    .run { modify(it, 5, Buy) }

on(WidgetItemThirdClickEvent::class)
    .args(3823)
    .run { modify(it, 5, Sell) }

/**
 * Buy/sell 10.
 */
on(WidgetItemFourthClickEvent::class)
    .args(3900)
    .run { modify(it, 10, Buy) }

on(WidgetItemFourthClickEvent::class)
    .args(3823)
    .run { modify(it, 10, Sell) }


