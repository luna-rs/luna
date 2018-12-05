package api.shop

import api.predef.*
import io.luna.game.model.def.ItemDefinition
import io.luna.game.model.item.IndexedItem

/**
 * The receiver for the [ShopReceiver.sell] closure.
 *
 * @author lare96
 */
class SellReceiver(private val shop: ShopReceiver) {

    /**
     * The added items.
     */
    private val items = ArrayList<IndexedItem>()

    /**
     * The next index to add an item to.
     */
    private var index = 0

    /**
     * Creates an [AmountBuilder] for id#[id].
     */
    private fun itemId(id: Int): AmountBuilder {
        return when (itemDef(id).isTradeable) {
            true -> AmountBuilder(id, items, index++)
            false -> throw IllegalStateException("Item ($id) in shop (${shop.name}) cannot be sold.")
        }
    }

    /**
     * Creates an [AmountBuilder] for an item whose name matches [name]. The syntax "id#" can be used
     * to append by identifier instead.
     */
    fun item(name: String, noted: Boolean = false): AmountBuilder {
        if (name.startsWith("id#")) {
            return itemId(name.drop(3).toInt())
        }
        return ItemDefinition.ALL
            .lookup { it.isTradeable && it.name == name && it.isNoted == noted }
            .map { AmountBuilder(it.id, items, index++) }
            .orElseThrow { NoSuchElementException("Item ($name) in shop (${shop.name}) was not valid or found.") }
    }

    /**
     * Returns [items] converted to a typed array.
     */
    fun getItems() = items.toTypedArray()

    /**
     * Adds to the list of shop items, once the amount is determined.
     */
    class AmountBuilder(private val id: Int,
                        private val items: MutableList<IndexedItem>,
                        private val index: Int) {

        /**
         * Retrieves the amount and adds the item to the shop.
         */
        infix fun x(amount: Int) = items.add(IndexedItem(index, id, amount))

    }
}