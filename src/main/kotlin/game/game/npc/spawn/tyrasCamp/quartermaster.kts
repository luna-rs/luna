package game.npc.spawn.tyrasCamp

import api.shop.dsl.ShopHandler
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy

/**
 * Quartermaster's Stores in the Tyras Camp.
 */
ShopHandler.create("Quartermaster's Stores") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.SLOW
    currency = Currency.COINS
    merchantsAccessOnly { combatLevel > 65 }

    sell {
        "Pot" x 50
        "Jug" x 20
        "Shears" x 30
        "Tinderbox" x 30
        "Bread" x 100
        "Bronze halberd" x 100
        "Iron halberd" x 100
        "Steel halberd" x 100
        "Black halberd" x 100
        "Mithril halberd" x 70
        "Adamant halberd" x 70
        "Rune halberd" x 20
        "Dragon halberd" x 10
    }

    open {
        npc2 += 1208
    }
}
