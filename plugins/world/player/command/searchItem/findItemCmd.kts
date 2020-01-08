package world.player.command.searchItem

import api.predef.*
import io.luna.game.event.impl.WidgetItemClickEvent
import io.luna.game.event.impl.WidgetItemClickEvent.*
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.inter.AmountInputInterface
import world.player.command.cmd

/**
 * Spawn an item.
 */
fun spawn(msg: WidgetItemClickEvent, amount: Int? = null) {
    val plr = msg.plr
    val id = msg.itemId
    val inv = plr.inventory
    when (amount) {
        null -> inv.add(Item(id, Int.MAX_VALUE))
        else -> inv.add(Item(id, amount))
    }
}

/**
 * Determines if the search interface is open.
 */
fun isInterfaceOpen(plr: Player) = plr.interfaces.isOpen(SearchResultInterface::class)

/**
 * A command that displays items on the banking interface, for easier item spawning.
 */
cmd("finditem", RIGHTS_DEV) {
    val search = getInputFrom(0)
    if (search.length > 1) {
        plr.interfaces.open(SearchResultInterface(search))
    } else {
        plr.sendMessage("Search term must be more than 1 character.")
    }
}

/**
 * Spawn 1.
 */
on(WidgetItemFirstClickEvent::class)
    .filter { widgetId == 5382 && isInterfaceOpen(plr) }
    .then { spawn(this, 1) }

/**
 * Spawn 5.
 */
on(WidgetItemSecondClickEvent::class)
    .filter { widgetId == 5382 && isInterfaceOpen(plr) }
    .then { spawn(this, 5) }

/**
 * Spawn 10.
 */
on(WidgetItemThirdClickEvent::class)
    .filter { widgetId == 5382 && isInterfaceOpen(plr) }
    .then { spawn(this, 10) }

/**
 * Spawn all.
 */
on(WidgetItemFourthClickEvent::class)
    .filter { widgetId == 5382 && isInterfaceOpen(plr) }
    .then { spawn(this) }

/**
 * Spawn (x).
 */
on(WidgetItemFifthClickEvent::class)
    .filter { widgetId == 5382 && isInterfaceOpen(plr) }
    .then {
        plr.interfaces.open(object : AmountInputInterface() {
            override fun onAmountInput(player: Player, value: Int) = spawn(this@then, value)
        })
    }