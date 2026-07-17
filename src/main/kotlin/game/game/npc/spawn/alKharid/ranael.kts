package game.npc.spawn.alKharid

import api.shop.dsl.ShopHandler
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy

/**
 * Ranael's Super Skirt Store in Al-Kharid.
 */
ShopHandler.create("Ranael's Super Skirt Store.") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.SLOW
    currency = Currency.COINS

    sell {
        "Bronze plateskirt" x 50
        "Iron plateskirt" x 30
        "Steel plateskirt" x 20
        "Black plateskirt" x 10
        "Mithril plateskirt" x 10
        "Adamant plateskirt" x 10
    }

    open {
        npc2 += 544
    }
}
