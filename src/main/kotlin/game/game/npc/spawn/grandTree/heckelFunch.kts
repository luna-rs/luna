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
        "Brandy" x 10
        "Gin" x 10
        "Vodka" x 10
        "Whisky" x 10
        "Pineapple" x 10
        "Equa leaves" x 20
        "Orange" x 20
        "Lemon" x 20
        "Lime" x 20
        "Dwellberries" x 5
        "Cocktail shaker" x 10
        "Chocolate bar" x 20
        "Chocolate dust" x 10
        "Pot of cream" x 5
        "Bucket of milk" x 5
        "Knife" x 5
        "Cocktail glass" x 20
    }

    open {
        npc2 += 603
    }
}
