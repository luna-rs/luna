package game.npc.spawn.voidKnightOutpost

import api.shop.dsl.ShopHandler
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy

/**
 * The Archery store at the Void Knights' Outpost, run by a squire.
 */
ShopHandler.create("Archery store") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.SLOW
    currency = Currency.COINS
    merchantsAccessOnly()

    sell {
        "Bronze arrowtips" x 100
        "Iron arrowtips" x 100
        "Steel arrowtips" x 100
        "Mithril arrowtips" x 100
        "Adamant arrowtips" x 30
        "Rune arrowtips" x 10
    }

    open {
        npc2 += 3796
    }
}
