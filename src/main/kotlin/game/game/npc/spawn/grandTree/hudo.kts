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
        "Gianne dough" x 50
        "Equa leaves" x 20
        1933 x 3 // Pot of flour
        "Gnome spice" x 10
        "Onion" x 10
        "Potato" x 10
        1965 x 10 // Cabbage
        "Tomato" x 10
        "Cheese" x 10
        "Lime" x 10
        "Orange" x 10
        "Lemon" x 10
        "Pineapple" x 10
        "Dwellberries" x 10
        "Cocktail shaker" x 10
        "Chocolate bar" x 20
        "Chocolate dust" x 10
        "Pot of cream" x 5
        "Bucket of milk" x 5
        "Knife" x 5
        "Gianne's cook book" x 5
        "Batta tin" x 5
    }

    open {
        npc2 += 600
    }
}
