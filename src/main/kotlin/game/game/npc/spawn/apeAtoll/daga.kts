package game.npc.spawn.apeAtoll

import api.shop.dsl.ShopHandler
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy

/**
 * Daga's Scimitar Smithy in Ape Atoll.
 */
ShopHandler.create("Daga's Scimitar Smithy") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.SLOW
    currency = Currency.COINS
    merchantsAccessOnly { combatLevel > 65 }

    sell {
        "Bronze scimitar" x 100
        "Iron scimitar" x 100
        "Steel scimitar" x 80
        "Mithril scimitar" x 60
        "Dragon scimitar" x 10
    }

    open {
        npc2 += 1434
    }
}
