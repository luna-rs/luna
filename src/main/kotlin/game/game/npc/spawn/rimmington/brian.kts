package game.npc.spawn.rimmington

import api.shop.dsl.ShopHandler
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy

/**
 * Brian's archery shop in Rimmington.
 */
ShopHandler.create("Brian's Archery Supplies.") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.SLOW
    currency = Currency.COINS

    sell {
        "Steel arrow" x 15_000
        "Mithril arrow" x 10_000
        "Adamant arrow" x 8000
        "Oak shortbow" x 400
        "Oak longbow" x 40
        "Willow shortbow" x 30
        "Willow longbow" x 30
        "Maple shortbow" x 20
        "Maple longbow" x 20
    }

    open {
        npc2 += 1860
    }
}