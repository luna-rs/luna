import api.*
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
    val inter = plr.interfaces.get(OfferTradeInterface::class)
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
    .args(3322)
    .run { trade(it, 1, Add) }

on(WidgetItemFirstClickEvent::class)
    .args(3415)
    .run { trade(it, 1, Remove) }

/**
 * Offer 5.
 */
on(WidgetItemSecondClickEvent::class)
    .args(3322)
    .run { trade(it, 5, Add) }

on(WidgetItemSecondClickEvent::class)
    .args(3415)
    .run { trade(it, 5, Remove) }

/**
 * Offer 10.
 */
on(WidgetItemThirdClickEvent::class)
    .args(3322)
    .run { trade(it, 10, Add) }

on(WidgetItemThirdClickEvent::class)
    .args(3415)
    .run { trade(it, 10, Remove) }

/**
 * Offer all.
 */
on(WidgetItemFourthClickEvent::class)
    .args(3322)
    .run { trade(it, -1, Add) }

on(WidgetItemFourthClickEvent::class)
    .args(3415)
    .run { trade(it, -1, Remove) }

/**
 * Offer (x).
 */
on(WidgetItemFifthClickEvent::class)
    .args(3322)
    .run {
        it.plr.interfaces.open(object : AmountInputInterface() {
            override fun onAmountInput(player: Player, value: Int) =
                trade(it, value, Add)
        })
    }

on(WidgetItemFifthClickEvent::class)
    .args(3415)
    .run {
        it.plr.interfaces.open(object : AmountInputInterface() {
            override fun onAmountInput(player: Player, value: Int) =
                trade(it, value, Remove)
        })
    }