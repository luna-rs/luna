import OfferItem.Change.ADD
import OfferItem.Change.REMOVE
import api.*
import io.luna.game.event.impl.WidgetItemClickEvent
import io.luna.game.event.impl.WidgetItemClickEvent.WidgetItemFifthClickEvent
import io.luna.game.event.impl.WidgetItemClickEvent.WidgetItemFirstClickEvent
import io.luna.game.event.impl.WidgetItemClickEvent.WidgetItemFourthClickEvent
import io.luna.game.event.impl.WidgetItemClickEvent.WidgetItemSecondClickEvent
import io.luna.game.event.impl.WidgetItemClickEvent.WidgetItemThirdClickEvent
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.inter.AmountInputInterface
import io.luna.game.model.mob.inter.OfferTradeInterface

/**
 * An enum representing the different modifications made to trades.
 */
enum class Change {
    ADD, REMOVE
}

/**
 * Either adds or removes an item from the trade screen.
 */
fun trade(msg: WidgetItemClickEvent, amount: Int, change: Change) {
    val plr = msg.plr
    val inter = plr.interfaces.get(OfferTradeInterface::class)
    if (inter != null) {
        val newAmount = if (amount == -1) plr.inventory.computeAmountForId(msg.itemId) else amount
        when (change) {
            ADD -> inter.add(plr, msg.index, newAmount)
            REMOVE -> inter.remove(plr, msg.index, newAmount)
        }
    }
}

/**
 * Offer 1.
 */
on(WidgetItemFirstClickEvent::class)
    .args(3322)
    .run { trade(it, 1, ADD) }

on(WidgetItemFirstClickEvent::class)
    .args(3415)
    .run { trade(it, 1, REMOVE) }

/**
 * Offer 5.
 */
on(WidgetItemSecondClickEvent::class)
    .args(3322)
    .run { trade(it, 5, ADD) }

on(WidgetItemSecondClickEvent::class)
    .args(3415)
    .run { trade(it, 5, REMOVE) }

/**
 * Offer 10.
 */
on(WidgetItemThirdClickEvent::class)
    .args(3322)
    .run { trade(it, 10, ADD) }

on(WidgetItemThirdClickEvent::class)
    .args(3415)
    .run { trade(it, 10, REMOVE) }

/**
 * Offer all.
 */
on(WidgetItemFourthClickEvent::class)
    .args(3322)
    .run { trade(it, -1, ADD) }

on(WidgetItemFourthClickEvent::class)
    .args(3415)
    .run { trade(it, -1, REMOVE) }

/**
 * Offer (x).
 */
on(WidgetItemFifthClickEvent::class)
    .args(3322)
    .run {
        it.plr.interfaces.open(object : AmountInputInterface() {
            override fun onAmountInput(player: Player, value: Int) =
                trade(it, value, ADD)
        })
    }

on(WidgetItemFifthClickEvent::class)
    .args(3415)
    .run {
        it.plr.interfaces.open(object : AmountInputInterface() {
            override fun onAmountInput(player: Player, value: Int) =
                trade(it, value, REMOVE)
        })
    }