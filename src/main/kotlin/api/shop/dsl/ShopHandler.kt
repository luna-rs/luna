package api.shop.dsl

/**
 * Handles shop related functions.
 *
 * @author lare96
 */
object ShopHandler {

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