package world.player.item.shops

import api.predef.*
import api.predef.ext.*
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
    .filter { widgetId == 3900 }
    .then { modify(this, 1, Buy) }

on(WidgetItemSecondClickEvent::class)
    .filter { widgetId == 3823 }
    .then { modify(this, 1, Sell) }

/**
 * Buy/sell 5.
 */
on(WidgetItemThirdClickEvent::class)
    .filter { widgetId == 3900 }
    .then { modify(this, 5, Buy) }

on(WidgetItemThirdClickEvent::class)
    .filter { widgetId == 3823 }
    .then { modify(this, 5, Sell) }

/**
 * Buy/sell 10.
 */
on(WidgetItemFourthClickEvent::class)
    .filter { widgetId == 3900 }
    .then { modify(this, 10, Buy) }

on(WidgetItemFourthClickEvent::class)
    .filter { widgetId == 3823 }
    .then { modify(this, 10, Sell) }


