package api.shop.dsl

import io.luna.game.model.def.ItemDefinition

/**
 * The receiver for the [ShopReceiver.sell] closure.
 *
 * @author lare96
 */
class SellReceiver(private val shop: ShopReceiver, private val noted: Boolean) {

    /**
     * Returns an instance of this receiver that works with noted items.
     */
    fun noted(func: SellReceiver.() -> Unit) = func(SellReceiver(shop, true))

    /**
     * Adds an item to the shop.
     */
    infix fun String.x(amount: Int) {
        val id = ItemDefinition.ALL
            .lookup { it.isTradeable && it.name == this && it.isNoted == noted }
            .map { it.id }
            .orElseThrow { NoSuchElementException("Item ($this) in shop (${shop.name}) was not valid or found.") }
        id.x(amount)
    }

    /**
     * Adds an item to the shop.
     */
    infix fun Int.x(amount: Int) {
        shop.addItem(this, amount)
    }
}