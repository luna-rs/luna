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
fun withdraw(msg: WidgetItemClickEvent, amount: Int? = null) {
    val plr = msg.plr
    val id = msg.itemId
    val bank = plr.bank
    if (bank.isOpen) {
        val index = msg.index
        when (amount) {
            null -> bank.withdraw(index, bank.computeAmountForId(id))
            else -> bank.withdraw(index, amount)
        }
    } else if (plr.rights >= RIGHTS_DEV) {
        // For the "::search_item" command (it uses the banking interface).
        // TODO decouple from this, add to searchItem
        val inv = plr.inventory
        when (amount) {
            null -> inv.add(Item(id, 1_000_000))
            else -> inv.add(Item(id, amount))
        }
    }
}

/**
 * Withdraw/deposit 1.
 */
on(WidgetItemFirstClickEvent::class)
    .filter { widgetId == 5064 }
    .then { deposit(this, 1) }

on(WidgetItemFirstClickEvent::class)
    .filter { widgetId == 5382 }
    .then { withdraw(this, 1) }

/**
 * Withdraw/deposit 5.
 */
on(WidgetItemSecondClickEvent::class)
    .filter { widgetId == 5064 }
    .then { deposit(this, 5) }

on(WidgetItemSecondClickEvent::class)
    .filter { widgetId == 5382 }
    .then { withdraw(this, 5) }

/**
 * Withdraw/deposit 10.
 */
on(WidgetItemThirdClickEvent::class)
    .filter { widgetId == 5064 }
    .then { deposit(this, 10) }

on(WidgetItemThirdClickEvent::class)
    .filter { widgetId == 5382 }
    .then { withdraw(this, 10) }


/* Withdraw/deposit all. */
on(WidgetItemFourthClickEvent::class)
    .filter { widgetId == 5064 }
    .then { deposit(this, -1) }

on(WidgetItemFourthClickEvent::class)
    .filter { widgetId == 5382 }
    .then { withdraw(this, -1) }

/**
 * Withdraw/deposit (x).
 */
on(WidgetItemFifthClickEvent::class)
    .filter { widgetId == 5064 }
    .then {
        plr.interfaces.open(object : AmountInputInterface() {
            override fun onAmountInput(player: Player, value: Int) = deposit(this@then, value)
        })
    }

on(WidgetItemFifthClickEvent::class)
    .filter { widgetId == 5382 }
    .then {
        plr.interfaces.open(object : AmountInputInterface() {
            override fun onAmountInput(player: Player, value: Int) = withdraw(this@then, value)
        })
    }