import api.predef.*
import io.luna.game.event.item.WidgetItemClickEvent
import io.luna.game.event.item.WidgetItemClickEvent.*
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.inter.AmountInputInterface

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
 * Withdraw an item.
 */
fun withdraw(msg: WidgetItemClickEvent, amount: Int? = null) {
    val plr = msg.plr
    val id = msg.itemId
    val index = msg.index
    when (amount) {
        null -> plr.bank.withdraw(index, plr.bank.computeAmountForId(id))
        else -> plr.bank.withdraw(index, amount)
    }
}

/**
 * Withdraw/deposit 1.
 */
on(WidgetItemFirstClickEvent::class)
    .filter { widgetId == 5064 && plr.bank.isOpen }
    .then { deposit(this, 1) }

on(WidgetItemFirstClickEvent::class)
    .filter { widgetId == 5382 && plr.bank.isOpen }
    .then { withdraw(this, 1) }

/**
 * Withdraw/deposit 5.
 */
on(WidgetItemSecondClickEvent::class)
    .filter { widgetId == 5064 && plr.bank.isOpen }
    .then { deposit(this, 5) }

on(WidgetItemSecondClickEvent::class)
    .filter { widgetId == 5382 && plr.bank.isOpen }
    .then { withdraw(this, 5) }

/**
 * Withdraw/deposit 10.
 */
on(WidgetItemThirdClickEvent::class)
    .filter { widgetId == 5064 && plr.bank.isOpen }
    .then { deposit(this, 10) }

on(WidgetItemThirdClickEvent::class)
    .filter { widgetId == 5382 && plr.bank.isOpen }
    .then { withdraw(this, 10) }

/**
 * Withdraw/deposit all.
 */
on(WidgetItemFourthClickEvent::class)
    .filter { widgetId == 5064 && plr.bank.isOpen }
    .then { deposit(this) }

on(WidgetItemFourthClickEvent::class)
    .filter { widgetId == 5382 && plr.bank.isOpen }
    .then { withdraw(this) }

/**
 * Withdraw/deposit (x).
 */
on(WidgetItemFifthClickEvent::class)
    .filter { widgetId == 5064 && plr.bank.isOpen }
    .then {
        plr.interfaces.open(object : AmountInputInterface() {
            override fun onAmountInput(player: Player, value: Int) = deposit(this@then, value)
        })
    }

on(WidgetItemFifthClickEvent::class)
    .filter { widgetId == 5382 && plr.bank.isOpen }
    .then {
        plr.interfaces.open(object : AmountInputInterface() {
            override fun onAmountInput(player: Player, value: Int) = withdraw(this@then, value)
        })
    }