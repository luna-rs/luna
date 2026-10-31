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
    botAccess = { false }

    sell {
        "Feather" x 10_000
    }

    open {
        npc2 += 517
    }
}
