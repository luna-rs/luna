package game.npc.spawn.grandTree

import api.shop.dsl.ShopHandler
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy

/**
 * Hudo's Grand Tree Groceries in the Grand Tree.
 */
ShopHandler.create("Grand Tree Groceries") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.SLOW
    currency = Currency.COINS
    merchantsAccessOnly()

    sell {
        "Knife" x 10
    }

    open {
        npc2 += 600
    }
}
