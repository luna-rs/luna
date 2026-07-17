package game.npc.spawn.championsGuild

import api.shop.dsl.ShopHandler
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy

/**
 * Valaine's Shop of Champions on the 2nd floor of the Champions' Guild.
 */
ShopHandler.create("Valaine's Shop of Champions.") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.SLOW
    currency = Currency.COINS

    sell {
        1021 x 20 // Blue cape
        "Black full helm" x 10
        "Black platelegs" x 10
        "Adamant platebody" x 10
    }

    open {
        npc2 += 536
    }
}
