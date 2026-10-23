package game.npc.spawn.shantayPass

import api.shop.dsl.ShopHandler
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy

/**
 * Shantay Pass Shop in Shantay Pass.
 */
ShopHandler.create("Shantay Pass Shop") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.SLOW
    currency = Currency.COINS

    sell {
        "Waterskin(4)" x 100
        "Waterskin(0)" x 100
        "Jug of water" x 10
        "Bowl of water" x 10
        "Bucket of water" x 10
        "Knife" x 10
        "Desert shirt" x 100
        "Desert robe" x 100
        "Desert boots" x 100
        "Bronze bar" x 100
        "Feather" x 5000
        "Hammer" x 10
        "Bucket" zero 10
        "Bowl" zero 10
        "Jug" zero 10
        "Shantay pass" x 500
        "Rope" x 20
    }

    open {
        npc2 += 836
        npc2 += 837
        npc2 += 838
    }
}
