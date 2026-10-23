package game.npc.spawn.falador

import api.shop.dsl.ShopHandler
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy

/**
 * Herquin's gem shop in Falador.
 */
ShopHandler.create("Herquin's Gems.") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.SLOW
    currency = Currency.COINS
    merchantsAccessOnly(true)

    sell {
        "Uncut sapphire" x 50
        "Uncut emerald" x 40
        "Uncut ruby" x 30
        "Uncut diamond" x 20
        "Sapphire" x 30
        "Emerald" x 25
        "Ruby" x 20
        "Diamond" x 15
    }

    open {
        npc2 += 584
    }
}