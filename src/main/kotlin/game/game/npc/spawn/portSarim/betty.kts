package game.npc.spawn.portSarim

import api.shop.dsl.ShopHandler
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy

/**
 * Betty's magic shop in Port Sarim.
 */
ShopHandler.create("Betty's Magic Emporium.") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.SLOW
    currency = Currency.COINS
    merchantsAccessOnly()

    sell {
        "Fire rune" x 5000
        "Water rune" x 5000
        "Air rune" x 5000
        "Earth rune" x 5000
        "Mind rune" x 5000
        "Body rune" x 5000
        "Chaos rune" x 250
        "Death rune" x 250
        "Eye of newt" x 300
        579 x 10 // Blue wizard hat.
    }

    open {
        npc2 += 583
    }
}