package game.npc.spawn.edgeville

import api.shop.dsl.ShopHandler
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy

/**
 * Nurmof's Pickaxe Shop in the Dwarven Mine.
 */
ShopHandler.create("Nurmof's Pickaxe Shop.") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.SLOW
    currency = Currency.COINS

    sell {
        "Bronze pickaxe" x 60
        "Iron pickaxe" x 50
        "Steel pickaxe" x 40
        "Mithril pickaxe" x 30
        "Adamant pickaxe" x 20
        "Rune pickaxe" x 10
    }

    open {
        npc2 += 594
    }
}