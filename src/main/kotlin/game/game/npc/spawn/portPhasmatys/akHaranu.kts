package game.npc.spawn.portPhasmatys

import api.shop.dsl.ShopHandler
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy

/**
 * Ak-Haranu's Exotic Shop located on the Port Phasmatys docks.
 */
ShopHandler.create("Ak-Haranu's Exotic Shop.") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.SLOW
    currency = Currency.COINS
    merchantsAccessOnly(true) { combatLevel > 75 }

    sell {
        "Bolt rack" x 5000
    }

    open {
        npc2 += 1688
    }
}
