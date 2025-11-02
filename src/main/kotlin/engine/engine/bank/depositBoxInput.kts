package engine.bank

import api.predef.*
import api.predef.ext.*
import io.luna.game.event.impl.WidgetItemClickEvent
import io.luna.game.event.impl.WidgetItemClickEvent.*
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.overlay.NumberInput

/**
 * Deposit an item.
 */
fun deposit(msg: WidgetItemClickEvent, amount: Int? = null) {
    val inv = msg.plr.inventory
    val bank = msg.plr.bank
    when (amount) {
        null -> bank.deposit(msg.index, inv.computeAmountForId(msg.itemId))
        else -> bank.deposit(msg.index, amount)
    }
}

/**
 * Deposit 1.
 */
on(WidgetItemFirstClickEvent::class)
    .filter { widgetId == 7423 && DepositBoxInterface::class in plr.overlays }
    .then { deposit(this, 1) }

/**
 * Deposit 5.
 */
on(WidgetItemSecondClickEvent::class)
    .filter { widgetId == 7423 && DepositBoxInterface::class in plr.overlays }
    .then { deposit(this, 5) }

/**
 * Deposit 10.
 */
on(WidgetItemThirdClickEvent::class)
    .filter { widgetId == 7423 && DepositBoxInterface::class in plr.overlays }
    .then { deposit(this, 10) }

/**
 * Deposit all.
 */
on(WidgetItemFourthClickEvent::class)
    .filter { widgetId == 7423 && DepositBoxInterface::class in plr.overlays }
    .then { deposit(this) }

/**
 * Deposit (x).
 */
on(WidgetItemFifthClickEvent::class)
    .filter { widgetId == 7423 && DepositBoxInterface::class in plr.overlays }
    .then {
        plr.overlays.open(object : NumberInput() {
            override fun input(player: Player, value: Int) = deposit(this@then, value)
        })
    }