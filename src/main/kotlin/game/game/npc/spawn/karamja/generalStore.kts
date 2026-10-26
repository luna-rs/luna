package game.npc.spawn.karamja

import api.predef.*
import api.shop.dsl.ShopHandler
import game.npc.spawn.GeneralStore
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy

/**
 * The general store in Musa Point on Karamja, run by a shop keeper and an assistant.
 */
ShopHandler.create("Karamja General Store") {
    buy = BuyPolicy.ALL
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS

    sell {
        "Pot" x 5 every 10
        "Jug" x 2 every 100
        "Shears" x 2 every 100
        "Bucket" x 3 every 10
        "Bowl" x 2 every 50
        "Cake tin" x 2 every 50
        "Tinderbox" x 2 every 100
        "Chisel" x 2 every 100
        "Hammer" x 5 every 100
    }

    open {
        npc2 += 532
        npc2 += 533
    }
}

listOf(532, 533).forEach { id ->
    npc1(id) { GeneralStore.greet(plr, id, "Karamja General Store") }
}
