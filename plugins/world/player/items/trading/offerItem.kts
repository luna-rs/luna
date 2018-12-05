import api.predef.*
import io.luna.game.event.impl.WidgetItemClickEvent
import io.luna.game.event.impl.WidgetItemClickEvent.*
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.inter.AmountInputInterface
import io.luna.game.model.mob.inter.OfferTradeInterface

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
 * Either adds or removes an item from the trade screen.
 */
fun trade(msg: WidgetItemClickEvent, amount: Int, mod: Mod) {
    val plr = msg.plr
    val inter = plr.getInterface(OfferTradeInterface::class)
    if (inter != null) {
        val newAmount = if (amount == -1) plr.inventory.computeAmountForId(msg.itemId) else amount
        when (mod) {
            Add -> inter.add(plr, msg.index, newAmount)
            Remove -> inter.remove(plr, msg.index, newAmount)
        }
    }
}

/**
 * Offer 1.
 */
on(WidgetItemFirstClickEvent::class)
    .condition { it.widgetId == 3322 }
    .then { trade(it, 1, Add) }

on(WidgetItemFirstClickEvent::class)
    .condition { it.widgetId == 3415 }
    .then { trade(it, 1, Remove) }

/**
 * Offer 5.
 */
on(WidgetItemSecondClickEvent::class)
    .condition { it.widgetId == 3322 }
    .then { trade(it, 5, Add) }

on(WidgetItemSecondClickEvent::class)
    .condition { it.widgetId == 3415 }
    .then { trade(it, 5, Remove) }

/**
 * Offer 10.
 */
on(WidgetItemThirdClickEvent::class)
    .condition { it.widgetId == 3322 }
    .then { trade(it, 10, Add) }

on(WidgetItemThirdClickEvent::class)
    .condition { it.widgetId == 3415 }
    .then { trade(it, 10, Remove) }

/**
 * Offer all.
 */
on(WidgetItemFourthClickEvent::class)
    .condition { it.widgetId == 3322 }
    .then { trade(it, -1, Add) }

on(WidgetItemFourthClickEvent::class)
    .condition { it.widgetId == 3415 }
    .then { trade(it, -1, Remove) }

/**
 * Offer (x).
 */
on(WidgetItemFifthClickEvent::class)
    .condition { it.widgetId == 3322 }
    .then {
        it.plr.openInterface(object : AmountInputInterface() {
            override fun onAmountInput(player: Player, value: Int) =
                trade(it, value, Add)
        })
    }

on(WidgetItemFifthClickEvent::class)
    .condition { it.widgetId == 3415 }
    .then {
        it.plr.openInterface(object : AmountInputInterface() {
            override fun onAmountInput(player: Player, value: Int) =
                trade(it, value, Remove)
        })
    }