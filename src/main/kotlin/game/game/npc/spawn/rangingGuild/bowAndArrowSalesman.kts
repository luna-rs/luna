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

    sell {
        "Arrow shaft" x 1000
        "Bronze arrowtips" x 100
        "Iron arrowtips" x 100
        "Steel arrowtips" x 100
        "Mithril arrowtips" x 100
        "Adamant arrowtips" x 30
        "Rune arrowtips" x 10
        "Bronze arrow" x 1000
        "Iron arrow" x 500
        "Steel arrow" x 500
        "Mithril arrow" x 500
        "Adamant arrow" x 450
        "Rune arrow" x 400
        "Bronze brutal" x 0
        "Iron brutal" x 0
        "Steel brutal" x 0
        "Black brutal" x 0
        "Mithril brutal" x 0
        "Adamant brutal" x 0
        "Rune brutal" x 0
        "Shortbow" x 20
        "Oak shortbow" x 20
        "Willow shortbow" x 20
        "Longbow" x 20
        "Oak longbow" x 20
        "Comp ogre bow" x 0
        "Willow longbow" x 20
    }

    open {
        npc2 += 683
    }
}
