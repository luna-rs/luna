package api.shop.dsl

import io.luna.game.model.item.IndexedItem

/**
 * Handles shop related functions.
 *
 * @author lare96
 */
object ShopHandler {

    /**
     * A pending item awaiting registration within a shop.
     */
    class PendingShopItem(index: Int, id: Int, startAmount: Int, val maxStockAmount: Int) :
        IndexedItem(index, id, startAmount) {

        /**
         * How many ticks it takes to restock one of this item, or `-1` to use the shop's restock policy rate.
         */
        var restockRate = -1
    }

    /**
     * Initializes a new [ShopReceiver]. The entry point of shop creation.
     */
    fun create(name: String, init: ShopReceiver.() -> Unit) {
        // Build the shop.
        val builder = ShopReceiver(name)
        init(builder)

        // Register the shop.
        builder.register()
    }
}