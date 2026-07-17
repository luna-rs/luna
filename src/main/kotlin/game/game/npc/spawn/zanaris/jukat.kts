package game.npc.spawn.zanaris

import api.shop.dsl.ShopHandler
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy

/**
 * Jukat in Zanaris.
 */
ShopHandler.create("Jukat") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.SLOW
    currency = Currency.COINS
    merchantsAccessOnly { combatLevel > 65 }

    sell {
        "Dragon longsword" x 10
        "Dragon dagger" x 10
    }

    open {
        npc2 += 564
    }
}
