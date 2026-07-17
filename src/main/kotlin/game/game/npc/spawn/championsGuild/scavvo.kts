package game.npc.spawn.championsGuild

import api.shop.dsl.ShopHandler
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy

/**
 * Scavvo's Rune Store in the Champions' Guild.
 */
ShopHandler.create("Scavvo's Rune Store.") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.SLOW
    currency = Currency.COINS
    merchantsAccessOnly { combatLevel > 45 }

    sell {
        "Rune plateskirt" x 10
        "Rune platelegs" x 10
        "Rune mace" x 10
        "Rune chainbody" x 10
        "Rune longsword" x 10
        "Rune sword" x 10
        "Green d'hide chaps" x 10
        "Green d'hide vamb" x 10
        "Coif" x 10
    }

    open {
        npc2 += 537
    }
}
