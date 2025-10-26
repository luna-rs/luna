package game.player.command.findItem

import api.predef.*
import api.predef.ext.*
import io.luna.game.event.impl.WidgetItemClickEvent
import io.luna.game.event.impl.WidgetItemClickEvent.*
import io.luna.game.model.def.GameObjectDefinition
import io.luna.game.model.def.ItemDefinition
import io.luna.game.model.def.NpcDefinition
import io.luna.game.model.item.Bank.DynamicBankInterface
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.inter.NumberInputInterface
import kotlin.streams.toList

/**
 * A standard interface representing the result of a search.
 */
class SearchResultInterface(private val searchToken: String) :
    DynamicBankInterface("Search results for '$searchToken' ...") {

    override fun buildDisplayItems(plr: Player): List<Item> {
        // Perform search here, filter items based on search token.
        val itemsFound = ItemDefinition.ALL
            .stream()
            .filter { it.id > 0 && !it.isNoted && it.name.lowercase().contains(searchToken) }
            .map { Item(it.id, 1) }
            .toList()

        val resultCount = itemsFound.size
        val maxResults = plr.bank.capacity()
        if (resultCount > maxResults) {
            // Truncate results.
            plr.sendMessage("Too many results ($resultCount) for search term '$searchToken'! The search has been truncated.")
            return  itemsFound.dropLast(resultCount - maxResults)
        } else {
            // Display as usual.
            plr.sendMessage("Found $resultCount results for search term '$searchToken'.")
            return itemsFound
        }
    }
}

/**
 * Spawn an item.
 */
fun spawn(msg: WidgetItemClickEvent, amount: Int? = null) {
    val plr = msg.plr
    val id = msg.itemId
    val item = Item(id, amount ?: Int.MAX_VALUE)
    plr.giveItem(item)
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
 * A command that searches for definition ids and displays them on the quest journal.
 */
cmd("finddef", RIGHTS_DEV) {
    val type = args[0].lowercase().trim()
    val search = getInputFrom(1).lowercase().trim()
    val matches = arrayListOf<Pair<Int, String>>()
    when (type) {
        "game/obj", "object", "objects" -> GameObjectDefinition.ALL.stream()
            .filter { it.name.lowercase().contains(search) }
            .forEach { matches.add(it.id to it.name) }

        "game/item", "items" -> ItemDefinition.ALL.stream().filter { it.name.lowercase().contains(search) }
            .forEach { matches.add(it.id to it.name) }

        "game/npc", "npcs" -> NpcDefinition.ALL.stream().filter { it.name.lowercase().contains(search) }
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
        plr.interfaces.open(object : NumberInputInterface() {
            override fun onAmountInput(player: Player, value: Int) = spawn(this@then, value)
        })
    }