package api.shop.dsl

import io.luna.game.model.item.Item

/**
 * The receiver for the [ShopReceiver.sell] closure.
 *
 * @author lare96
 */
class SellReceiver(private val shop: ShopReceiver, private val noted: Boolean) {

    /**
     * Returns a new [SellReceiver] instance that works with noted items.
     */
    fun noted(func: SellReceiver.() -> Unit) = func(SellReceiver(shop, true))

    /**
     * Adds an item to the shop.
     */
    infix fun String.x(amount: Int) {
        Item.findId(this, noted).x(amount)
    }

    /**
     * Adds an item to the shop.
     */
    infix fun Int.x(amount: Int) {
        shop.addItem(this, amount)
    }
}