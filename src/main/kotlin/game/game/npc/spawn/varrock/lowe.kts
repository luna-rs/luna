package game.npc.spawn.varrock

import api.shop.dsl.ShopHandler
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy

/**
 * Lowe's Archery Emporium in Varrock.
 */
ShopHandler.create("Lowe's Archery Emporium") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.SLOW
    currency = Currency.COINS

    sell {
        "Bronze arrow" x 2000
        "Iron arrow" x 1500
        "Steel arrow" x 1000
        "Mithril arrow" x 800
        "Adamant arrow" x 600
        "Bolts" x 1500
        "Shortbow" x 4
        "Longbow" x 4
        "Oak shortbow" x 3
        "Oak longbow" x 3
        "Willow shortbow" x 2
        "Willow longbow" x 2
        "Maple shortbow" x 1
        "Maple longbow" x 1
        "Crossbow" x 2
    }

    open {
        npc2 += 550
    }
}
