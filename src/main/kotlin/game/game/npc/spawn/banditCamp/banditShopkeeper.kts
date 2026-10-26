package game.npc.spawn.banditCamp

import api.predef.*
import api.shop.dsl.ShopHandler
import game.npc.spawn.GeneralStore
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy

/**
 * Bandit Bargains, the general store in the Bandit Camp in the Kharidian Desert.
 */
ShopHandler.create("Bandit Bargains") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS
    merchantsAccessOnly()

    sell {
        "Waterskin(4)" x 5 every 100
        "Waterskin(0)" x 5 every 100
        "Jug of water" x 5 every 100
        "Bowl of water" x 5 every 100
        "Bucket of water" x 5 every 100
        "Jug" x 5 every 100
        "Bowl" x 5 every 100
        "Bucket" x 5 every 100
        "Desert boots" x 5 every 500
        "Desert shirt" x 5 every 500
        "Desert robe" x 5 every 500
        "Knife" x 5 every 100
    }

    open {
        npc2 += 1917
    }
}

// TODO Add the bandit shopkeeper's dialogue. Until then, talking to him opens his store.
npc1(1917) { GeneralStore.open(plr, "Bandit Bargains") }
