package game.npc.spawn.taverley

import api.shop.dsl.ShopHandler
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy

/**
 * Jatix's Herblore Shop in Taverley.
 */
ShopHandler.create("Jatix's Herblore Shop.") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.SLOW
    currency = Currency.COINS

    sell {
        "Vial" x 8000
        "Vial of water" x 7500
        "Pestle and mortar" x 30
        "Eye of newt" x 8000
    }

    open {
        npc2 += 587
    }
}
