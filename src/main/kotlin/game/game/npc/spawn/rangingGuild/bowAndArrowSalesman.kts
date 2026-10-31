package game.npc.spawn.rangingGuild

import api.predef.*
import api.shop.dsl.ShopHandler
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy

/**
 * The Bow and Arrow salesman's archery shop in the Ranging Guild.
 */
ShopHandler.create("Archery shop") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.SLOW
    currency = Currency.COINS
    botAccess = { ranged.level >= 40 }

    sell {
        "Bronze arrowtips" x 100
        "Iron arrowtips" x 100
        "Steel arrowtips" x 100
        "Mithril arrowtips" x 100
        "Adamant arrowtips" x 30
        "Rune arrowtips" x 10
    }

    open {
        npc2 += 683
    }
}
