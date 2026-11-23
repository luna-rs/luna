package game.npc.spawn.shilo

import api.shop.dsl.ShopHandler
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy

/**
 * Fernahei's fishing shop in Shilo Village.
 */
ShopHandler.create("Fishing shop") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.SLOW
    currency = Currency.COINS
    // TODO Once Shilo Village is added, let in bots that have finished the quest.

    sell {
        "Fishing rod" x 5
        "Fly fishing rod" x 5
        "Fishing bait" x 200
        "Feather" x 10_000
        "Raw trout" x 0
        "Raw pike" x 0
        "Raw salmon" x 0
    }

    open {
        npc2 += 517
    }
}
