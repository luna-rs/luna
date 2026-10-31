package game.npc.spawn.grandTree

import api.shop.dsl.ShopHandler
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy

/**
 * Heckel Funch's Fine Groceries in the Grand Tree.
 */
ShopHandler.create("Funch's Fine Groceries") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.SLOW
    currency = Currency.COINS
    merchantsAccessOnly()

    sell {
        "Knife" x 10
    }

    open {
        npc2 += 603
    }
}
