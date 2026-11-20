package api.bot.action

import io.luna.game.model.def.ItemDefinition
import io.luna.game.model.item.shop.Shop
import io.luna.game.model.item.shop.ShopManager

/**
 * Resolves a shop's physical stock for a bot's wanted item while keeping native prices and item notes.
 *
 * @author lare96
 */
internal fun Shop.stockIdFor(id: Int): Int {
    return stockForms(id).firstOrNull { stockId ->
        val slot = items.computeIndexForId(stockId)
        slot >= 0 && items[slot]?.let { !it.isDynamic && it.amount > 0 } == true
    } ?: id
}

/**
 * Finds shops that carry either physical form, including sold-in stock.
 */
internal fun ShopManager.sourcesForStock(id: Int): List<Shop> {
    val forms = stockForms(id)
    return values().filter { shop -> forms.any { it in shop.itemIds } }
}

/**
 * Returns the requested item followed by its other physical form, when one exists.
 */
internal fun stockForms(id: Int): IntArray {
    val alternate = ItemDefinition.ALL.get(id).map { it.unnotedId.orElse(it.notedId.orElse(id)) }.orElse(id)
    return if (alternate == id) intArrayOf(id) else intArrayOf(id, alternate)
}
