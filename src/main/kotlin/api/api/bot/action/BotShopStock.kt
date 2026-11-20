package api.bot.action

import io.luna.game.model.def.ItemDefinition
import io.luna.game.model.item.shop.Shop

/**
 * Resolves a shop's physical stock for a bot's wanted item while keeping native prices and item notes.
 *
 * @author lare96
 */
internal fun Shop.stockIdFor(id: Int): Int {
    if (items.computeAmountForId(id) > 0) return id
    val note = ItemDefinition.ALL.get(id).map { it.notedId.orElse(id) }.orElse(id)
    return if (items.computeAmountForId(note) > 0) note else id
}
