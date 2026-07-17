package game.npc.spawn.varrock

import api.shop.dsl.ShopHandler
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy

/**
 * Fancy Clothes Store in Varrock.
 */
ShopHandler.create("Fancy Clothes Store") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.SLOW
    currency = Currency.COINS

    sell {
        "Chef's hat" x 10
        579 x 3 // Blue wizard hat
        1023 x 1 // Yellow cape
        "Grey wolf fur" x 3
        "Bear fur" x 3
        "Needle" x 3
        "Thread" x 100
        "Leather gloves" x 10
        "Leather boots" x 10
        428 x 3 // Priest gown (bottom)
        426 x 3 // Priest gown (top)
        "Brown apron" x 1
        "Pink skirt" x 5
        "Black skirt" x 3
        "Blue skirt" x 2
        1007 x 4 // Red cape
        "Eye patch" x 3
    }

    open {
        npc2 += 554
    }
}
