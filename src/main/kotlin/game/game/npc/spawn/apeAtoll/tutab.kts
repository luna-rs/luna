package game.npc.spawn.apeAtoll

import api.shop.dsl.ShopHandler
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy

/**
 * Tutab's Magical Market in Ape Atoll.
 */
ShopHandler.create("Tutab's Magical Market") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.SLOW
    currency = Currency.COINS
    merchantsAccessOnly()

    sell {
        "Fire rune" x 10_000
        "Water rune" x 10_000
        "Air rune" x 10_000
        "Earth rune" x 10_000
        "Law rune" x 2500
        "Eye of gnome" x 100
        //"Monkey dentures" x 1000
        //"Monkey talisman" x 500
    }

    open {
        npc2 += 1435
    }
}
