package game.npc.spawn.legendsGuild

import api.shop.dsl.ShopHandler
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy

/**
 * Legends Guild General Store on the 2nd floor of the Legends' Guild.
 */
ShopHandler.create("Legends Guild General Store.") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.SLOW
    currency = Currency.COINS
    botAccess = { combatLevel > 30 }

    sell {
        "Swordfish" x 200
        "Apple pie" x 50
        "Attack potion(3)" x 30
        "Steel arrow" x 5000
    }

    open {
        npc2 += 932
    }
}
