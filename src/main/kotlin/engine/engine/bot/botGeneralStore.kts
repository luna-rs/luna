package engine.bot

import api.shop.dsl.ShopHandler
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy


/**
 * The bot general store containing utility items such as keys that a bot may need.
 */
ShopHandler.create("Bot General Store") {
    buy = BuyPolicy.ALL
    restock = RestockPolicy.FAST
    currency = Currency.COINS

    sell {
        "Anti-dragon shield" x 50
        "Dusty key" x 50
        "Brass key" x 50
    }
}