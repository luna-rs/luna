package game.npc.spawn.heroesGuild

import api.shop.dsl.ShopHandler
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy

/**
 * Happy Heroes' H'emporium on the 2nd floor of the Heroes' Guild.
 */
ShopHandler.create("Happy Heroes' H'emporium.") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.SLOW
    currency = Currency.COINS
    merchantsAccessOnly { combatLevel > 65 }

    sell {
        "Dragon battleaxe" x 10
        "Dragon mace" x 10
    }

    open {
        npc2 += 797
    }
}
