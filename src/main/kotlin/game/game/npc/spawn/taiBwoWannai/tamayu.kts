package game.npc.spawn.taiBwoWannai

import api.shop.dsl.ShopHandler
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy

/**
 * Tamayu's Spear Stall in Tai Bwo Wannai.
 */
ShopHandler.create("Tamayu's Spear Stall") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.SLOW
    currency = Currency.COINS

    sell {
        "Bronze spear(kp)" x 100
        "Iron spear(kp)" x 100
        "Steel spear(kp)" x 50
        "Mithril spear(kp)" x 20
        "Adamant spear(kp)" x 100
        "Rune spear(kp)" x 100
        "Cleaning cloth" x 100
    }

    open {
        npc2 += 1168
    }
}
