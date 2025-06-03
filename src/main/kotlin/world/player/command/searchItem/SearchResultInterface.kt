package world.player.command.searchItem

import io.luna.game.model.def.ItemDefinition
import io.luna.game.model.item.Bank.DynamicBankInterface
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
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
            .filter { it.id > 0 && !it.isNoted && it.name.toLowerCase().contains(searchToken) }
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