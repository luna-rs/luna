package game.npc.spawn.alKharid

import api.shop.dsl.ShopHandler
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy

/**
 * Zeke's Superior Scimitars in Al-Kharid.
 */
ShopHandler.create("Zeke's Superior Scimitars.") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.SLOW
    currency = Currency.COINS

    sell {
        "Bronze scimitar" x 50
        "Iron scimitar" x 30
        "Steel scimitar" x 20
        "Mithril scimitar" x 10
    }

    open {
        npc2 += 541
    }
}
