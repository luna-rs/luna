package api.predef

import api.shop.ShopReceiver

/**
 * Initializes a new [ShopReceiver]. The entry point of shop creation.
 */
fun shop(name: String, init: ShopReceiver.() -> Unit) {
    // Build the shop.
    val builder = ShopReceiver(name)
    init(builder)

    // Register the shop.
    builder.register()
}