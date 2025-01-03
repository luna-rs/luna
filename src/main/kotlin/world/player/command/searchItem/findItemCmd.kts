package world.player.command.searchItem

import api.predef.*
import api.predef.ext.*
import io.luna.game.event.impl.WidgetItemClickEvent
import io.luna.game.event.impl.WidgetItemClickEvent.*
import io.luna.game.model.def.ItemDefinition
import io.luna.game.model.def.NpcDefinition
import io.luna.game.model.def.GameObjectDefinition
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.inter.AmountInputInterface

/**
 * Spawn an item.
 */
fun spawn(msg: WidgetItemClickEvent, amount: Int? = null) {
    val plr = msg.plr
    val id = msg.itemId
    val item = Item(id, amount ?: Int.MAX_VALUE)
    if (!plr.inventory.hasSpaceFor(item)) {
        plr.sendMessage("${item.itemDef.name}(x${numF(item.amount)}) Sent to your bank.")
        plr.bank.add(item)
    } else {
        plr.sendMessage("${item.itemDef.name}(x${numF(item.amount)}) Sent to your inventory.")
        plr.inventory.add(item)
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

// TODO move this  cmd
/**
 * A command that searches for definition ids and displays them on the quest journal.
 */
cmd("finddef", RIGHTS_DEV) {
    val type = args[0].toLowerCase().trim()
    val search = getInputFrom(1).toLowerCase().trim()
    val matches = arrayListOf<Pair<Int, String>>()
    when (type) {
        "obj", "object", "objects" -> GameObjectDefinition.ALL.stream().filter { it.name.toLowerCase().contains(search) }
            .forEach { matches.add(it.id to it.name) }

        "item", "items" -> ItemDefinition.ALL.stream().filter { it.name.toLowerCase().contains(search) }
            .forEach { matches.add(it.id to it.name) }

        "npc", "npcs" -> NpcDefinition.ALL.stream().filter { it.name.toLowerCase().contains(search) }
            .forEach { matches.add(it.id to it.name) }
    }
    if (matches.isNotEmpty()) {
        for (next in matches) {
            plr.sendMessage("${next.first} - ${next.second}")
        }
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