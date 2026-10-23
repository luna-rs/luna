package game.npc.spawn.varrock

import api.shop.dsl.ShopHandler
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy

/**
 * Varrock Swordshop in Varrock.
 */
ShopHandler.create("Varrock Swordshop") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.SLOW
    currency = Currency.COINS

    sell {
        "Bronze sword" x 50
        "Iron sword" x 40
        "Steel sword" x 40
        "Black sword" x 30
        "Mithril sword" x 30
        "Adamant sword" x 20
        "Bronze longsword" x 40
        "Iron longsword" x 30
        "Steel longsword" x 30
        "Black longsword" x 20
        "Mithril longsword" x 20
        "Adamant longsword" x 10
        "Bronze dagger" x 100
        "Iron dagger" x 60
        "Steel dagger" x 50
        "Black dagger" x 40
        "Mithril dagger" x 30
        "Adamant dagger" x 20
    }

    open {
        npc2 += 551
    }
}
