package game.npc.spawn.barbarianVillage

import api.shop.dsl.ShopHandler
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy

/**
 * Helmet Shop in Barbarian Village.
 */
ShopHandler.create("Helmet Shop.") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.SLOW
    currency = Currency.COINS

    sell {
        "Bronze med helm" x 50
        "Iron med helm" x 30
        "Steel med helm" x 30
        "Mithril med helm" x 10
        "Adamant med helm" x 10
        "Bronze full helm" x 40
        "Iron full helm" x 30
        "Steel full helm" x 20
        "Mithril full helm" x 10
        "Adamant full helm" x 10
    }

    open {
        npc2 += 538
    }
}
