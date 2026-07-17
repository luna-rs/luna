package game.npc.spawn.falador

import api.shop.dsl.ShopHandler
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy

/**
 * Wayne's chainmail shop in Falador.
 */
ShopHandler.create("Wayne's Chains! - Chainmail specialist.") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.SLOW
    currency = Currency.COINS

    sell {
        "Bronze chainbody" x 30
        "Iron chainbody" x 20
        "Steel chainbody" x 10
        "Black chainbody" x 10
        "Mithril chainbody" x 10
        "Adamant chainbody" x 10
    }

    open {
        npc2 += 581
    }
}