import api.predef.*
import io.luna.game.event.impl.WidgetItemClickEvent
import io.luna.game.event.impl.WidgetItemClickEvent.*
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.inter.AmountInputInterface

/**
 * Deposit an item.
 */
fun deposit(msg: WidgetItemClickEvent, amount: Int) {
    val inv = msg.plr.inventory
    val bank = msg.plr.bank
    if (bank.isOpen) {
        when (amount) {
            -1 -> bank.deposit(msg.index, inv.computeAmountForId(msg.itemId))
            else -> bank.deposit(msg.index, amount)
        }
    }
}

/**
 * Withdraw an item.
 */
fun withdraw(msg: WidgetItemClickEvent, amount: Int) {
    val plr = msg.plr
    val inv = plr.inventory
    val bank = plr.bank
    if (bank.isOpen) {
        when (amount) {
            -1 -> bank.withdraw(msg.index, bank.computeAmountForId(msg.itemId))
            else -> bank.withdraw(msg.index, amount)
        }
    } else if (plr.rights >= RIGHTS_DEV) {
        // For the "::search_item" command (it uses the banking interface).
        when (amount) {
            -1 -> inv.add(Item(msg.itemId, 1_000_000))
            else -> inv.add(Item(msg.itemId, amount))
        }
    }
}

/**
 * Withdraw/deposit 1.
 */
on(WidgetItemFirstClickEvent::class)
    .condition { it.widgetId == 5064 }
    .then { deposit(it, 1) }

on(WidgetItemFirstClickEvent::class)
    .condition { it.widgetId == 5382 }
    .then { withdraw(it, 1) }

/**
 * Withdraw/deposit 5.
 */
on(WidgetItemSecondClickEvent::class)
    .condition { it.widgetId == 5064 }
    .then { deposit(it, 5) }

on(WidgetItemSecondClickEvent::class)
    .condition { it.widgetId == 5382 }
    .then { withdraw(it, 5) }

/**
 * Withdraw/deposit 10.
 */
on(WidgetItemThirdClickEvent::class)
    .condition { it.widgetId == 5064 }
    .then { deposit(it, 10) }

on(WidgetItemThirdClickEvent::class)
    .condition { it.widgetId == 5382 }
    .then { withdraw(it, 10) }


/* Withdraw/deposit all. */
on(WidgetItemFourthClickEvent::class)
    .condition { it.widgetId == 5064 }
    .then { deposit(it, -1) }

on(WidgetItemFourthClickEvent::class)
    .condition { it.widgetId == 5382 }
    .then { withdraw(it, -1) }

/**
 * Withdraw/deposit (x).
 */
on(WidgetItemFifthClickEvent::class)
    .condition { it.widgetId == 5064 }
    .then {
        val interfaces = it.plr.interfaces
        interfaces.open(object : AmountInputInterface() {
            override fun onAmountInput(player: Player, value: Int) = deposit(it, value)
        })
    }

on(WidgetItemFifthClickEvent::class)
    .condition { it.widgetId == 5382 }
    .then {
        val interfaces = it.plr.interfaces
        interfaces.open(object : AmountInputInterface() {
            override fun onAmountInput(player: Player, value: Int) = withdraw(it, value)
        })
    }