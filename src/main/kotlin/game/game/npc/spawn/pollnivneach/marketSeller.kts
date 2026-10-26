package game.npc.spawn.pollnivneach

import api.predef.*
import api.shop.dsl.ShopHandler
import game.npc.spawn.GeneralStore
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy

/**
 * The Pollnivneach General Store, run by a market seller.
 */
ShopHandler.create("Pollnivneach General Store") {
    buy = BuyPolicy.ALL
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS
    merchantsAccessOnly()

    sell {
        "Pot" x 3 every 100
        "Jug" x 2 every 100
        "Waterskin(3)" x 20 every 10
        "Desert shirt" x 3 every 12
        "Desert boots" x 2 every 100
        "Bucket" x 12 every 400
        "Fake beard" x 11 every 10
        "Karidian headpiece" x 12 every 10
        "Cheese" x 5 every 200
        "Lime" x 5 every 200
        "Orange" x 5 every 200
        "Tomato" x 5 every 200
        "Jug of water" x 5 every 10
        "Bowl of water" x 7 every 10
        "Bucket of water" x 8 every 10
    }

    open {
        npc2 += 1866
    }
}

// TODO Add the market seller's dialogue. Until then, talking to him opens his store.
npc1(1866) { GeneralStore.open(plr, "Pollnivneach General Store") }
