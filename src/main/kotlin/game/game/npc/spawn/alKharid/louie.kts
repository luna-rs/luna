package game.npc.spawn.alKharid

import api.shop.dsl.ShopHandler
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy

/**
 * Louie's Armoured Legs Bazaar in Al-Kharid.
 */
ShopHandler.create("Louie's Armoured Legs Bazaar.") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.SLOW
    currency = Currency.COINS

    sell {
        "Bronze platelegs" x 50
        "Iron platelegs" x 30
        "Steel platelegs" x 20
        "Black platelegs" x 10
        "Mithril platelegs" x 10
        "Adamant platelegs" x 10
    }

    open {
        npc2 += 542
    }
}
