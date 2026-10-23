package game.npc.spawn.legendsGuild

import api.shop.dsl.ShopHandler
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy

/**
 * Legends Guild Shop of Useful Items on the 3rd floor of the Legends' Guild (west side).
 */
ShopHandler.create("Legends Guild Shop of Useful Items.") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.SLOW
    currency = Currency.COINS
    merchantsAccessOnly { combatLevel > 65 }

    sell {
        "Mithril seeds" x 60
        "Dusty key" x 50
        "Maze key" x 30
        "Shield right half" x 10
        "Cape of legends" x 30
    }

    open {
        npc2 += 933
    }
}
