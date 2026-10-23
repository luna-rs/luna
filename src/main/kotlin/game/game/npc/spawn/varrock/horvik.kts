package game.npc.spawn.varrock

import api.shop.dsl.ShopHandler
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy

/**
 * Horvik's Armour Shop in Varrock.
 */
ShopHandler.create("Horvik's Armour Shop.") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.SLOW
    currency = Currency.COINS

    sell {
        "Bronze chainbody" x 50
        "Iron chainbody" x 30
        "Steel chainbody" x 30
        "Mithril chainbody" x 10
        "Bronze platebody" x 30
        "Iron platebody" x 10
        "Steel platebody" x 10
        "Black platebody" x 10
        "Mithril platebody" x 10
        "Iron platelegs" x 10
        "Studded body" x 10
        "Studded chaps" x 10
    }

    open {
        npc2 += 549
    }
}
