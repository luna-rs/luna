package api.shop

import api.predef.*
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy
import io.luna.game.model.item.shop.Shop

/**
 * The receiver for the [shop] closure that encapsulates shop creation.
 *
 * @author lare96
 */
class ShopReceiver {

    /**
     * The name.
     */
    var name: String = "Shop"

    /**
     * The buy policy.
     */
    var buy = BuyPolicy.EXISTING

    /**
     * The restock policy.
     */
    var restock = RestockPolicy.DEFAULT!!

    /**
     * The currency.
     */
    var currency = Currency.COINS

    /**
     * The [SellReceiver] instance.
     */
    private val sellReceiver = SellReceiver(this)

    /**
     * The [OpenReceiver] instance.
     */
    private val openReceiver = OpenReceiver()

    /**
     * Initializes a new [SellReceiver]. Allows for adding the initial items to the shop.
     */
    fun sell(init: SellReceiver.() -> Unit) {
        init(sellReceiver)
    }

    /**
     * Initializes a new [OpenReceiver]. Allows for opening this shop with npc or object clicks.
     */
    fun open(init: OpenReceiver.() -> Unit) {
        init(openReceiver)
    }

    /**
     * Registers this shop. Is invoked implicitly once the shop closure exits.
     */
    fun register() {

        // Create and initialize shop.
        val shop = Shop(world, name, restock, buy, currency)
        shop.init(sellReceiver.getItems())

        // Add event listeners from the OpenReceiver.
        openReceiver.addListeners(shop)

        // Register shop!
        world.shops.register(shop)
    }
}