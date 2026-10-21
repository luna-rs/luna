package engine.trade

import api.predef.*
import api.predef.ext.*
import io.luna.game.event.impl.WidgetItemClickEvent
import io.luna.game.event.impl.WidgetItemClickEvent.*
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.overlay.NumberInput

/**
 * Represents either an 'add' or 'remove' modification.
 */
open class Mod

/**
 * Represents an 'add' modification.
 */
object Add : Mod()

/**
 * Represents a 'remove' modification.
 */
object Remove : Mod()

/**
 * Either adds or removes an item from the trade screen. An [amount] of -1 moves all of the clicked item.
 */
fun trade(msg: WidgetItemClickEvent, amount: Int, mod: Mod) {
    val plr = msg.plr
    val inter = plr.overlays[OfferTradeInterface::class]
    if (inter != null) {
        when (mod) {
            Add -> inter.add(plr, msg.index, amount)
            Remove -> inter.remove(plr, msg.index, amount)
        }
    }
}

/**
 * Offer 1.
 */
on(WidgetItemFirstClickEvent::class)
    .filter { widgetId == 3322 }
    .then { trade(this, 1, Add) }

on(WidgetItemFirstClickEvent::class)
    .filter { widgetId == 3415 }
    .then { trade(this, 1, Remove) }

/**
 * Offer 5.
 */
on(WidgetItemSecondClickEvent::class)
    .filter { widgetId == 3322 }
    .then { trade(this, 5, Add) }

on(WidgetItemSecondClickEvent::class)
    .filter { widgetId == 3415 }
    .then { trade(this, 5, Remove) }

/**
 * Offer 10.
 */
on(WidgetItemThirdClickEvent::class)
    .filter { widgetId == 3322 }
    .then { trade(this, 10, Add) }

on(WidgetItemThirdClickEvent::class)
    .filter { widgetId == 3415 }
    .then { trade(this, 10, Remove) }

/**
 * Offer all.
 */
on(WidgetItemFourthClickEvent::class)
    .filter { widgetId == 3322 }
    .then { trade(this, -1, Add) }

on(WidgetItemFourthClickEvent::class)
    .filter { widgetId == 3415 }
    .then { trade(this, -1, Remove) }

/**
 * Offer (x).
 */
on(WidgetItemFifthClickEvent::class)
    .filter { widgetId == 3322 }
    .then {
        plr.overlays.open(object : NumberInput() {
            override fun input(player: Player, value: Int) = trade(this@then, value, Add)
        })
    }

on(WidgetItemFifthClickEvent::class)
    .filter { widgetId == 3415 }
    .then {
        plr.overlays.open(object : NumberInput() {
            override fun input(player: Player, value: Int) = trade(this@then, value, Remove)
        })
    }