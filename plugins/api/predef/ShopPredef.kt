package api.predef

import api.shop.ShopReceiver

/**
 * Initializes a new [ShopReceiver]. The entry point of shop creation.
 */
fun shop(init: ShopReceiver.() -> Unit) {
    // Build the shop.
    val builder = ShopReceiver()
    init(builder)

    // Register the shop.
    builder.register()
}